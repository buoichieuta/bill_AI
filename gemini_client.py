"""
Gemini Client - dùng google.genai SDK mới
Tên model đúng cho API mới: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-2.5-pro-exp-03-25
"""
import io
import random
import time
from pathlib import Path
from typing import Optional, Union

from google import genai
from google.genai import types
from PIL import Image
from loguru import logger


# Chúng ta đã bỏ FALLBACK_MODELS để chỉ chạy đúng 1 model duy nhất (theo yêu cầu)


class GeminiClient:
    def __init__(self, config):
        self.config = config
        self.model_version = config.get('api.model_version', 'gemini-flash-latest')

        api_key = config.get('api.api_key')
        if not api_key:
            raise ValueError("❌ Chưa có GEMINI_API_KEY trong .env")

        self.client = genai.Client(api_key=api_key)
        self.current_model = self.model_version
        self.system_instruction = self._load_system_prompt(
            config.get('prompts.system_prompt_path')
        )
        self.gen_config = types.GenerateContentConfig(
            temperature=config.get('api.generation.temperature', 0.1),
            top_p=config.get('api.generation.top_p', 0.9),
            top_k=config.get('api.generation.top_k', 40),
            candidate_count=1,
            system_instruction=self.system_instruction,
        )
        logger.info(f"✅ GeminiClient sẵn sàng | model: {self.model_version}")

    # ── helpers ──────────────────────────────────

    def _load_system_prompt(self, prompt_path):
        if not prompt_path:
            return self._default_prompt()
        path = Path(prompt_path)
        if not path.exists():
            alt = Path(__file__).parent / "system_prompt_vn.txt"
            path = alt if alt.exists() else None
        if not path or not path.exists():
            logger.warning("⚠️ Không tìm thấy system prompt, dùng mặc định")
            return self._default_prompt()
        with open(path, 'r', encoding='utf-8') as f:
            content = f.read()
        logger.info(f"📄 System prompt: {path}")
        return content

    def _default_prompt(self):
        return (
            "Bạn là chuyên gia trích xuất thông tin hóa đơn Việt Nam. "
            "Trả về JSON với: SELLER, ADDRESS, TAX_CODE, INVOICE_NO, TIMESTAMP, "
            "PRODUCTS(PRODUCT/NUM/UNIT_PRICE/VALUE), TOTAL_COST, CASH_RECEIVED, CHANGE. "
            "CHỈ trả về JSON thuần túy, không markdown."
        )

    def _pil_to_bytes(self, image: Image.Image) -> bytes:
        if image.mode not in ('RGB',):
            image = image.convert('RGB')
        buf = io.BytesIO()
        image.save(buf, format='JPEG', quality=90)
        buf.seek(0)
        return buf.read()

    def _is_quota_error(self, e) -> bool:
        s = str(e)
        return "429" in s or "RESOURCE_EXHAUSTED" in s or "quota" in s.lower()

    def _is_not_found(self, e) -> bool:
        return "404" in str(e) or "NOT_FOUND" in str(e)

    # ── public ───────────────────────────────────

    def generate_content(self, image, prompt: str):
        image_bytes = self._pil_to_bytes(image) if isinstance(image, Image.Image) else image
        try:
            return self._call_with_retry(self.current_model, image_bytes, prompt)
        except Exception as e:
            if self._is_quota_error(e) or self._is_not_found(e):
                logger.error(f"⚡ {self.current_model} lỗi ({type(e).__name__}). Đã tắt chế độ tự động chuyển model.")
                raise RuntimeError(
                    f"❌ Model {self.current_model} thất bại do giới hạn API (Quota/404).\n"
                    "Cấu hình hiện tại chỉ chạy 1 model mặc định (không tự động thử model khác).\n"
                    f"Lỗi chi tiết: {e}"
                )
            raise

    def _call_with_retry(self, model_name: str, image_bytes: bytes, prompt: str, max_retries=3):
        image_part = types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg")
        base_delay = self.config.get('api.retry.base_delay', 1.0)
        max_delay  = self.config.get('api.retry.max_delay', 30.0)
        last_err   = None

        for attempt in range(max_retries + 1):
            try:
                response = self.client.models.generate_content(
                    model=model_name,
                    contents=[prompt, image_part],
                    config=self.gen_config,
                )
                if not self._is_valid(response):
                    raise ValueError("Response rỗng hoặc bị block")
                logger.info(f"✅ API OK | model={model_name} attempt={attempt+1}")
                return response

            except Exception as e:
                # Quota hoặc 404 → raise ngay để fallback xử lý
                if self._is_quota_error(e) or self._is_not_found(e):
                    raise
                last_err = e
                if attempt == max_retries:
                    raise

            delay = min(base_delay * (2 ** attempt), max_delay) + random.uniform(0, 1)
            logger.warning(f"⏳ Retry {attempt+1}/{max_retries} sau {delay:.1f}s: {last_err}")
            time.sleep(delay)

        raise RuntimeError(f"Hết retry: {last_err}")

    def _fallback(self, image_bytes: bytes, prompt: str):
        # Đã vô hiệu hóa fallback theo yêu cầu chỉ chạy 1 model
        pass

    def _is_valid(self, response) -> bool:
        try:
            return bool(response and response.text)
        except Exception:
            return False

    @property
    def model_info(self) -> dict:
        return {
            "model_version": self.model_version,
            "current_model": self.current_model,
            "using_fallback": self.current_model != self.model_version,
        }
