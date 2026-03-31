"""
Pipeline chính - điều phối toàn bộ quá trình nhận diện hóa đơn
"""
import re
import os
import json
import traceback
from typing import Dict, Any, Union, Tuple
from pathlib import Path

from PIL import Image
from loguru import logger

from config import Config
from preprocessor import ImageProcessor
from gemini_client import GeminiClient
from validator import DataValidator, DataNormalizer
from formatter import to_table


# ─────────────────────────────────────────────────────────────
# JSON Repair - sửa JSON lỗi từ LLM
# ─────────────────────────────────────────────────────────────

class JSONRepair:

    @staticmethod
    def extract_json_block(text: str) -> str:
        """Lấy JSON object đầu tiên từ text"""
        # Bỏ markdown fences
        text = re.sub(r'```(?:json)?\s*', '', text)
        text = re.sub(r'```\s*$', '', text, flags=re.MULTILINE)
        text = text.strip()

        start = text.find('{')
        if start == -1:
            raise ValueError(f"Không tìm thấy JSON. Raw: {text[:300]}")

        depth, in_str, escape = 0, False, False
        for i, ch in enumerate(text[start:], start):
            if escape:
                escape = False; continue
            if ch == '\\' and in_str:
                escape = True; continue
            if ch == '"':
                in_str = not in_str; continue
            if in_str:
                continue
            if ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]

        return text[start:]  # unclosed — thử parse anyway

    @staticmethod
    def fix_common(text: str) -> str:
        text = re.sub(r',\s*([}\]])', r'\1', text)          # trailing commas
        text = text.replace(': None', ': null')              # Python None
        text = text.replace(':None', ':null')
        text = text.replace(': True', ': true')
        text = text.replace(': False', ': false')
        text = re.sub(r"'([A-Z_]+)':", r'"\1":', text)      # single-quote keys
        return text


# ─────────────────────────────────────────────────────────────
# Main Extractor
# ─────────────────────────────────────────────────────────────

class GeminiInvoiceExtractor:
    """
    Pipeline nhận diện hóa đơn:
    ảnh → preprocess → Gemini API → parse JSON → validate → kết quả
    """

    def __init__(self, config: Config):
        self.config = config
        self.preprocessor = ImageProcessor()
        self.gemini_client = GeminiClient(config)
        self.validator = DataValidator()
        self.normalizer = DataNormalizer()
        self.json_repair = JSONRepair()

        self.max_size     = config.get('preprocessing.max_image_size', 1600)
        self.strict       = config.get('validation.strict_mode', False)
        self.auto_norm    = config.get('normalization.auto_normalize', True)

        logger.info("✅ GeminiInvoiceExtractor sẵn sàng")

    # ── Public ──────────────────────────────────────────────

    def extract(
        self,
        image_input: Union[str, Path, Image.Image],
        prompt: str = "Trích xuất toàn bộ thông tin hóa đơn này",
        return_raw: bool = False,
    ) -> Dict[str, Any]:
        """
        Trích xuất thông tin hóa đơn.

        Args:
            image_input : đường dẫn file | Path | PIL.Image
            prompt      : prompt gửi kèm ảnh
            return_raw  : nếu True, bỏ qua validate/normalize

        Returns:
            dict chứa thông tin hóa đơn + key '_meta'
        """
        logger.info("▶ Bắt đầu trích xuất hóa đơn...")

        # 1. Load & resize ảnh
        image = self._load_image(image_input)

        # 2. Gọi Gemini
        response = self.gemini_client.generate_content(image=image, prompt=prompt)

        # 3. Parse JSON (nhiều phương pháp fallback)
        data, method = self._parse_robust(response)
        logger.info(f"📋 Parse JSON thành công ({method})")

        if return_raw:
            return data

        # 4. Validate & normalize
        data = self._validate_normalize(data)

        # 5. Thêm metadata
        data['_meta'] = {
            'model': self.gemini_client.current_model,
            'parse': method,
            'fallback': self.gemini_client.model_info['using_fallback'],
        }

        n = len(data.get('PRODUCTS', []))
        logger.info(f"✅ Xong — {n} sản phẩm | model: {data['_meta']['model']}")
        return data

    def extract_and_display(self, image_input, prompt="Trích xuất toàn bộ thông tin hóa đơn này"):
        """Extract + in bảng ra terminal"""
        result = self.extract(image_input, prompt)
        try:
            to_table(result)
        except Exception as e:
            logger.warning(f"Không in bảng được: {e}")
            print(json.dumps(
                {k: v for k, v in result.items() if not k.startswith('_')},
                indent=2, ensure_ascii=False
            ))
        return result

    def save_result(self, result: Dict, output_path: Union[str, Path]) -> None:
        """Lưu kết quả ra file JSON"""
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        clean = {k: v for k, v in result.items() if not k.startswith('_')}
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(clean, f, indent=2, ensure_ascii=False, default=str)
        logger.info(f"💾 Đã lưu: {output_path}")

    # ── Private ─────────────────────────────────────────────

    def _load_image(self, source: Union[str, Path, Image.Image]) -> Image.Image:
        if isinstance(source, (str, Path)):
            path = str(source)
            if not os.path.exists(path):
                raise FileNotFoundError(f"Không tìm thấy file: {path}")
            if not self.preprocessor.validate_image(path):
                raise ValueError(f"File ảnh không hợp lệ: {path}")
            image = Image.open(path)
        elif isinstance(source, Image.Image):
            image = source
        else:
            raise TypeError(f"Kiểu không hỗ trợ: {type(source)}")

        # Chuyển về RGB
        if image.mode not in ('RGB',):
            image = image.convert('RGB')

        # Resize nếu quá lớn
        w, h = image.size
        if max(w, h) > self.max_size:
            scale = self.max_size / max(w, h)
            image = image.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
            logger.info(f"📐 Resize: {w}×{h} → {image.size}")

        return image

    def _parse_robust(self, response) -> Tuple[Dict, str]:
        """Parse JSON với 4 lớp fallback"""
        raw = response.text
        logger.debug(f"Raw response (300ch): {raw[:300]}")

        # 1. Parse thẳng
        try:
            return json.loads(raw.strip()), "direct"
        except json.JSONDecodeError:
            pass

        # 2. Extract JSON block
        try:
            block = self.json_repair.extract_json_block(raw)
            return json.loads(block), "extracted"
        except (json.JSONDecodeError, ValueError):
            pass

        # 3. Fix + parse
        try:
            block = self.json_repair.extract_json_block(raw)
            fixed = self.json_repair.fix_common(block)
            return json.loads(fixed), "repaired"
        except (json.JSONDecodeError, ValueError):
            pass

        # 4. Regex fallback
        logger.warning("⚠️ JSON parse thất bại, dùng regex fallback")
        return self._regex_fallback(raw), "regex"

    def _regex_fallback(self, text: str) -> Dict:
        """Lấy thông tin cơ bản bằng regex khi JSON parse thất bại"""
        result = {
            "SELLER": "", "ADDRESS": "", "TAX_CODE": None,
            "INVOICE_NO": "", "TIMESTAMP": "", "PRODUCTS": [],
            "TOTAL_COST": 0.0, "CASH_RECEIVED": None, "CHANGE": None,
            "_parse_error": "JSON parse failed"
        }
        for field, pattern in [
            ("SELLER",     r'"SELLER"\s*:\s*"([^"]+)"'),
            ("TIMESTAMP",  r'"TIMESTAMP"\s*:\s*"([^"]+)"'),
            ("INVOICE_NO", r'"INVOICE_NO"\s*:\s*"([^"]+)"'),
        ]:
            m = re.search(pattern, text)
            if m: result[field] = m.group(1)

        m = re.search(r'"TOTAL_COST"\s*:\s*([0-9.]+)', text)
        if m: result["TOTAL_COST"] = float(m.group(1))

        m = re.search(r'"PRODUCTS"\s*:\s*(\[.*?\])', text, re.DOTALL)
        if m:
            try: result["PRODUCTS"] = json.loads(m.group(1))
            except: pass

        return result

    def _validate_normalize(self, data: Dict) -> Dict:
        try:
            if not self.validator.validate_json_structure(data):
                if self.strict:
                    raise ValueError("Cấu trúc JSON thiếu field bắt buộc")

            data = self.validator.validate_field_types(data)

            if self.auto_norm:
                data["SELLER"] = str(data.get("SELLER", "")).strip()
                data["TOTAL_COST"] = self.normalizer.normalize_currency(data.get("TOTAL_COST", 0))
                for p in data.get("PRODUCTS", []):
                    p["VALUE"] = self.normalizer.normalize_currency(p.get("VALUE", 0))
                    if "UNIT_PRICE" in p:
                        p["UNIT_PRICE"] = self.normalizer.normalize_currency(p.get("UNIT_PRICE", 0))
            data = self._reconcile_product_amounts(data)

            products = data.get("PRODUCTS", [])
            if products and data.get("TOTAL_COST"):
                self.validator.validate_total(products, data["TOTAL_COST"])

        except Exception as e:
            logger.error(f"Validate lỗi: {e}")
            if self.strict:
                raise
        return data

    def _reconcile_product_amounts(self, data: Dict) -> Dict:
        products = data.get("PRODUCTS", [])
        if not isinstance(products, list):
            return data

        for p in products:
            if not isinstance(p, dict):
                continue

            try:
                num = int(float(p.get("NUM", 1)))
            except Exception:
                num = 1
            p["NUM"] = max(1, num)

            try:
                val = float(p.get("VALUE", 0) or 0)
            except Exception:
                val = 0.0

            up_raw = p.get("UNIT_PRICE", None)
            if up_raw is None:
                p["VALUE"] = round(val, 0)
                if p["NUM"] > 0 and val > 0:
                    p["UNIT_PRICE"] = round(val / p["NUM"], 0)
                continue

            try:
                up = float(up_raw or 0)
            except Exception:
                up = 0.0
            p["UNIT_PRICE"] = round(up, 0)

            expected = round(max(0.0, up) * p["NUM"], 0)
            if val <= 0 or abs(val - expected) > max(1000.0, expected * 0.02):
                p["VALUE"] = expected
            else:
                p["VALUE"] = round(val, 0)

        return data


# ─────────────────────────────────────────────────────────────
# Batch Processor
# ─────────────────────────────────────────────────────────────

class BatchProcessor:
    """Xử lý nhiều ảnh cùng lúc"""

    def __init__(self, extractor: GeminiInvoiceExtractor):
        self.extractor = extractor

    def process_directory(self, input_dir: Union[str, Path], output_dir=None, continue_on_error=True):
        input_dir = Path(input_dir)
        if output_dir:
            output_dir = Path(output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)

        files = []
        for ext in ['jpg', 'jpeg', 'png', 'bmp', 'JPG', 'JPEG', 'PNG']:
            files.extend(input_dir.glob(f"*.{ext}"))

        logger.info(f"📁 {len(files)} ảnh trong {input_dir}")
        results = {'total': len(files), 'success': 0, 'failed': 0, 'items': []}

        for i, f in enumerate(files, 1):
            logger.info(f"[{i}/{len(files)}] {f.name}")
            try:
                data = self.extractor.extract(f)
                if output_dir:
                    self.extractor.save_result(data, output_dir / f"{f.stem}.json")
                results['items'].append({'file': str(f), 'success': True, 'data': data})
                results['success'] += 1
            except Exception as e:
                logger.error(f"❌ {f.name}: {e}")
                results['items'].append({'file': str(f), 'success': False, 'error': str(e)})
                results['failed'] += 1
                if not continue_on_error:
                    raise

        logger.info(f"🏁 Xong: {results['success']}/{results['total']} thành công")
        return results
