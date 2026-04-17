import json
import re
import os
from typing import Any, Dict, List, Optional, Tuple
from PIL import Image
from loguru import logger
from src.utils import (
    _normalize_bbox,
    _bbox_overlap_ratio,
    _normalized_text,
    _parse_amount_from_text,
    _find_first_regex,
    TOTAL_KEYWORDS,
    CASH_KEYWORDS,
    CHANGE_KEYWORDS,
    TAX_CODE_PATTERNS,
    INVOICE_NO_PATTERNS,
    TIMESTAMP_PATTERNS
)

OCR_SYSTEM_PROMPT = (
    "You are a Vietnamese receipt OCR engine. "
    "Return only valid JSON with this exact shape: "
    '{"lines":[{"text":"...", "bbox":[x1,y1,x2,y2]}]}. '
    "Use integer pixel coordinates relative to the current image. "
    "Merge words that belong to the same printed line. "
    "Keep the natural reading order from top to bottom."
)

def get_extractor(api_key: str, model: str):
    """Khởi tạo Gemini extractor."""
    from config import Config
    from pipeline import GeminiInvoiceExtractor
    
    config = Config(api_key=api_key, model=model)
    return GeminiInvoiceExtractor(config)


def _call_gemini_with_system(gemini_client, image: Image.Image, prompt: str, system_instruction: str):
    from google.genai import types

    previous_config = gemini_client.gen_config
    gemini_client.gen_config = types.GenerateContentConfig(
        temperature=0.0,
        top_p=0.9,
        top_k=32,
        candidate_count=1,
        system_instruction=system_instruction,
    )
    try:
        return gemini_client.generate_content(image=image, prompt=prompt)
    finally:
        gemini_client.gen_config = previous_config


def _normalize_ocr_lines(payload: Any, w: int, h: int) -> List[Dict[str, Any]]:
    if isinstance(payload, dict):
        candidates = payload.get("lines") or payload.get("ocr_lines") or payload.get("items") or []
    elif isinstance(payload, list):
        candidates = payload
    else:
        candidates = []

    normalized = []
    for item in candidates:
        if not isinstance(item, dict):
            continue
        text = re.sub(r"\s+", " ", str(item.get("text", "")).strip())
        if not text:
            continue
        bbox = _normalize_bbox(item.get("bbox"), w, h)
        if not bbox:
            continue
        normalized.append({"text": text, "bbox": bbox})

    normalized.sort(key=lambda row: (row["bbox"][1], row["bbox"][0]))
    return normalized


def extract_ocr_lines(extractor, image: Image.Image, field_boxes: Optional[List[Dict[str, Any]]] = None):
    w, h = image.size
    field_hint = []
    for field in (field_boxes or [])[:40]:
        field_hint.append({
            "field": field.get("field"),
            "label": field.get("label"),
            "bbox": field.get("bbox"),
            "confidence": field.get("confidence"),
        })

    prompt = (
        f"Image size: width={w}, height={h}.\n"
        "Read this Vietnamese receipt and return OCR lines with bounding boxes.\n"
        "Each bbox must be [x1,y1,x2,y2] in integer pixels relative to this image.\n"
        "Keep one object per printed line. Ignore background.\n"
        f"Field detections from local model (optional hints): {json.dumps(field_hint, ensure_ascii=False)}"
    )

    response = _call_gemini_with_system(
        extractor.gemini_client,
        image,
        prompt,
        OCR_SYSTEM_PROMPT,
    )
    data, parse_method = extractor._parse_robust(response)
    lines = _normalize_ocr_lines(data, w, h)
    meta = {
        "ocr_parse_method": parse_method,
        "ocr_line_count": len(lines),
        "ocr_lines": lines,
    }
    return lines, meta


def _collect_text_from_box(ocr_lines: List[Dict[str, Any]], bbox: List[int]) -> str:
    matched = []
    for line in ocr_lines:
        if _bbox_overlap_ratio(line["bbox"], bbox) >= 0.4:
            matched.append(line)
    matched.sort(key=lambda row: (row["bbox"][1], row["bbox"][0]))
    return " ".join(item["text"] for item in matched).strip()


def build_rule_draft(
    ocr_lines: List[Dict[str, Any]],
    field_boxes: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    field_values = []
    for field in field_boxes or []:
        bbox = field.get("bbox")
        if not bbox:
            continue
        text = _collect_text_from_box(ocr_lines, bbox)
        if not text:
            continue
        field_values.append({
            "field": field.get("field"),
            "label": field.get("label"),
            "confidence": field.get("confidence"),
            "text": text,
            "bbox": bbox,
        })

    seller_hint = next((item["text"] for item in field_values if item["field"] in {"seller", "title"}), "")
    address_hint = next((item["text"] for item in field_values if item["field"] == "address"), "")
    invoice_hint = next((item["text"] for item in field_values if item["field"] == "invoice_no"), "")
    timestamp_hint = next((item["text"] for item in field_values if item["field"] == "timestamp"), "")
    total_hint_text = next((item["text"] for item in field_values if item["field"] == "total"), "")
    cash_hint_text = next((item["text"] for item in field_values if item["field"] == "cash_received"), "")
    change_hint_text = next((item["text"] for item in field_values if item["field"] == "change"), "")

    product_candidates = []
    for item in field_values:
        if item["field"] == "product_name":
            product_candidates.append(item["text"])
    if not product_candidates:
        for line in ocr_lines:
            normalized = _normalized_text(line["text"])
            amount = _parse_amount_from_text(line["text"])
            looks_like_total = any(keyword in normalized for keyword in TOTAL_KEYWORDS + CASH_KEYWORDS + CHANGE_KEYWORDS)
            if amount and not looks_like_total and len(line["text"]) >= 6:
                product_candidates.append(line["text"])
            if len(product_candidates) >= 12:
                break

    text_blob = "\n".join(line["text"] for line in ocr_lines)
    total_from_line = None
    cash_from_line = None
    change_from_line = None
    for line in ocr_lines:
        normalized = _normalized_text(line["text"])
        amount = _parse_amount_from_text(line["text"])
        if amount is None:
            continue
        if total_from_line is None and any(keyword in normalized for keyword in TOTAL_KEYWORDS):
            total_from_line = amount
        if cash_from_line is None and any(keyword in normalized for keyword in CASH_KEYWORDS):
            cash_from_line = amount
        if change_from_line is None and any(keyword in normalized for keyword in CHANGE_KEYWORDS):
            change_from_line = amount

    return {
        "SELLER_HINT": seller_hint or (ocr_lines[0]["text"] if ocr_lines else ""),
        "ADDRESS_HINT": address_hint,
        "TAX_CODE_HINT": _find_first_regex(_normalized_text(text_blob), TAX_CODE_PATTERNS),
        "INVOICE_NO_HINT": invoice_hint or _find_first_regex(_normalized_text(text_blob), INVOICE_NO_PATTERNS),
        "TIMESTAMP_HINT": timestamp_hint or _find_first_regex(text_blob, TIMESTAMP_PATTERNS),
        "CATEGORY_HINT": _classify_category_from_text(text_blob),
        "TOTAL_HINT": total_from_line if total_from_line is not None else _parse_amount_from_text(total_hint_text),
        "CASH_RECEIVED_HINT": cash_from_line if cash_from_line is not None else _parse_amount_from_text(cash_hint_text),
        "CHANGE_HINT": change_from_line if change_from_line is not None else _parse_amount_from_text(change_hint_text),
        "PRODUCT_CANDIDATES": product_candidates[:12],
        "FIELD_VALUES": field_values[:48],
    }


def _classify_category_from_text(text: str) -> str:
    """Phân loại category từ text OCR"""
    text_upper = text.upper()
    
    categories = {
        "Ăn uống": [
            "BBQ", "CAFÉ", "NHÀ HÀNG", "QUÁN", "ĂN", "UỐNG", "ĐỒ ĂN", "ĐỒ UỐNG",
            "CÀ PHÊ", "TRÀ", "NƯỚC", "BÁNH", "PHỞ", "CƠM", "MÌ", "GÀ", "BÒ",
            "HEINEKEN", "BIA", "RƯỢU", "SUSHI", "RESTAURANT", "NHÀ HÀNG", "QUÁN ĂN",
            "Đồ uống", "Đồ ăn", "CƠM CUỘN", "GÀ CHIÊN", "CÁ HỒI", "BÁNH HẢI SẢN"
        ],
        "Di chuyển": [
            "VÉ", "TAXI", "XE BUÝT", "MÁY BAY", "DI CHUYỂN", "VẬN TẢI",
            "GRAB", "BE", "BUS", "AIRLINE", "VÉ MÁY BAY", "VÉ XE",
            "NGƯỜI LỚN", "TRẺ EM"
        ],
        "Mua sắm": [
            "SIÊU THỊ", "CỬA HÀNG", "MUA SẮM", "BÁN LẺ", "MART", "SHOP",
            "CO.OP", "BIG C", "LOTTE", "VINMART", "KHĂN", "QUẦN ÁO", "ĐIỆN TỬ"
        ],
        "Y tế": [
            "BỆNH VIỆN", "PHÒNG KHÁM", "THUỐC", "BÁC SĨ", "Y TẾ", "KHÁM BỆNH"
        ],
        "Giải trí": [
            "RẠP CHIẾU PHIM", "CINEMA", "CGV", "LOTTEMART", "SÂN KHẤU", "CONCERT"
        ]
    }
    
    for cat, keywords in categories.items():
        for kw in keywords:
            if kw.upper() in text_upper:
                return cat
    
    return "Khác"


def load_extraction_prompt_template(prompt_template_path: Optional[os.PathLike] = None) -> str:
    if prompt_template_path and os.path.exists(prompt_template_path):
        with open(prompt_template_path, "r", encoding="utf-8") as f:
            return f.read()
    return (
        "Analyze this invoice image and extract the following information in JSON format:\n"
        "{\n"
        '  "SELLER": "",\n'
        '  "ADDRESS": "",\n'
        '  "TAX_CODE": "",\n'
        '  "INVOICE_NO": "",\n'
        '  "TIMESTAMP": "",\n'
        '  "CATEGORY": "",\n'
        '  "PRODUCTS": [{"PRODUCT": "", "NUM": 1, "UNIT_PRICE": 0, "VALUE": 0}],\n'
        '  "TOTAL_COST": 0,\n'
        '  "CASH_RECEIVED": 0,\n'
        '  "CHANGE": 0\n'
        "}\n\n"
        "Rules:\n"
        "1. Extract text exactly as shown in Vietnamese.\n"
        "2. For PRODUCTS, capture complete product descriptions including unit prices.\n"
        "3. NUM should be numeric (convert if needed).\n"
        "4. VALUE should be numeric without currency symbols.\n"
        "5. TOTAL_COST is the final total amount.\n"
        "6. Extract TAX_CODE and INVOICE_NO if visible.\n"
        "7. CATEGORY: Classify invoice type (Ăn uống, Di chuyển, Mua sắm, Y tế, Giải trí, Khác).\n"
        "8. If a field is not found, use empty string "" or empty array [].\n"
        "9. Preserve Vietnamese diacritics accurately.\n"
        "10. Keep only real line items in PRODUCTS (exclude subtotal/total/payment lines).\n\n"
        "OCR lines with bbox:\n{{OCR_LINES}}\n\n"
        "Rule-based draft:\n{{RULE_DRAFT}}\n\n"
        "Output only valid JSON, no additional text."
    )


def build_extraction_prompt(
    image: Image.Image,
    ocr_lines: List[Dict[str, Any]],
    rule_draft: Dict[str, Any],
    prompt_template_path: Optional[os.PathLike] = None,
    field_boxes: Optional[List[Dict[str, Any]]] = None,
) -> str:
    prompt = load_extraction_prompt_template(prompt_template_path)
    prompt = prompt.replace("{{OCR_LINES}}", json.dumps(ocr_lines[:120], ensure_ascii=False))
    prompt = prompt.replace("{{RULE_DRAFT}}", json.dumps(rule_draft, ensure_ascii=False))

    extras = {
        "image_size": {"width": image.size[0], "height": image.size[1]},
        "field_detections": (field_boxes or [])[:48],
    }
    prompt += (
        "\n\nAdditional constraints:\n"
        "- Use the image itself as the source of truth.\n"
        "- Use OCR lines and local field detections as supporting hints.\n"
        "- Keep PRODUCTS as real line items only.\n"
        "- Prefer TOTAL_COST from the final payable total, not subtotal.\n"
        f"\nContext:\n{json.dumps(extras, ensure_ascii=False)}"
    )
    return prompt
