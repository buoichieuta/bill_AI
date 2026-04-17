import re
import unicodedata
from typing import Any, Dict, List, Optional, Tuple

# ─────────────────────────────────────────────
# Constants
# ─────────────────────────────────────────────

FIELD_DETECTOR_HINTS = (
    "addr", "amount", "billid", "cashier", "datetime", "fax", "phone",
    "product", "price", "shop", "title", "unit", "money", "discount", "prefix"
)

DOCUMENT_DETECTOR_HINTS = {"receipt", "invoice", "document", "paper"}

FIELD_ROLE_MAP = {
    "ADDR": "address",
    "AMOUNT": "amount",
    "AMOUT": "amount",
    "BILLID": "invoice_no",
    "CASHIER": "cashier",
    "DATETIME": "timestamp",
    "FAX": "fax",
    "FPRICE": "unit_price",
    "NUMBER": "qty",
    "PHONE": "phone",
    "PRODUCT_NAME": "product_name",
    "RECEMONEY": "cash_received",
    "REMAMONEY": "change",
    "SHOP_NAME": "seller",
    "SUB_TPRICE": "subtotal",
    "TAMOUNT": "total",
    "TDISCOUNT": "discount",
    "TITLE": "title",
    "TPRICE": "amount",
    "UDISCOUNT": "discount",
    "UNIT": "unit",
    "UPRICE": "unit_price",
}

TOTAL_KEYWORDS = (
    "tong cong", "tong thanh toan", "tong tien", "thanh tien",
    "can thanh toan", "khach phai tra", "total", "grand total"
)

CASH_KEYWORDS = ("tien khach dua", "khach dua", "tien mat", "cash", "received")

CHANGE_KEYWORDS = ("tien thoi", "tra lai", "change")

TIMESTAMP_PATTERNS = (
    r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\s+\d{1,2}:\d{2}(?::\d{2})?\b",
    r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b",
)

INVOICE_NO_PATTERNS = (
    r"\b(?:số hóa đơn|hóa đơn số|số HD|HD số|invoice no|invoice number)\s*[:\-]?\s*([A-Z0-9\-/]{3,50})\b",
    r"\b([A-Z0-9]{3,}-?[0-9]{3,}(-[0-9]+)?)\b",
)

TAX_CODE_PATTERNS = (
    r"\b(?:mst|ma so thue|mã số thuế|tax code)\s*[:\-]?\s*([0-9]{8,14}(?:-[0-9]{1,3})?)\b",
    r"\b([0-9]{10,14}(?:-[0-9]{1,3})?)\b",
)

# ─────────────────────────────────────────────
# Utility Functions
# ─────────────────────────────────────────────

def _strip_accents(text: str) -> str:
    normalized = unicodedata.normalize("NFD", text or "")
    return "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")


def _normalized_text(text: str) -> str:
    clean = re.sub(r"\s+", " ", (text or "").strip()).lower()
    return _strip_accents(clean)


def _bbox_iou(box_a: List[int], box_b: List[int]) -> float:
    ax1, ay1, ax2, ay2 = box_a
    bx1, by1, bx2, by2 = box_b
    inter_x1 = max(ax1, bx1)
    inter_y1 = max(ay1, by1)
    inter_x2 = min(ax2, bx2)
    inter_y2 = min(ay2, by2)
    if inter_x2 <= inter_x1 or inter_y2 <= inter_y1:
        return 0.0

    inter = float((inter_x2 - inter_x1) * (inter_y2 - inter_y1))
    area_a = float(max(1, (ax2 - ax1) * (ay2 - ay1)))
    area_b = float(max(1, (bx2 - bx1) * (by2 - by1)))
    return inter / max(1.0, area_a + area_b - inter)


def _bbox_overlap_ratio(inner: List[int], outer: List[int]) -> float:
    ix1 = max(inner[0], outer[0])
    iy1 = max(inner[1], outer[1])
    ix2 = min(inner[2], outer[2])
    iy2 = min(inner[3], outer[3])
    if ix2 <= ix1 or iy2 <= iy1:
        return 0.0
    inter = float((ix2 - ix1) * (iy2 - iy1))
    inner_area = float(max(1, (inner[2] - inner[0]) * (inner[3] - inner[1])))
    return inter / inner_area


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(round(float(value)))
    except Exception:
        return default


def _normalize_bbox(bbox: Any, w: int, h: int) -> Optional[List[int]]:
    if not isinstance(bbox, (list, tuple)) or len(bbox) != 4:
        return None

    x1, y1, x2, y2 = (_safe_int(v) for v in bbox)
    x1 = max(0, min(x1, w - 1))
    y1 = max(0, min(y1, h - 1))
    x2 = max(1, min(x2, w))
    y2 = max(1, min(y2, h))
    if x2 <= x1 or y2 <= y1:
        return None
    return [x1, y1, x2, y2]


def _expand_box(x1: int, y1: int, x2: int, y2: int, w: int, h: int,
                pad_x_ratio: float = 0.1, pad_y_ratio: float = 0.12) -> Tuple[int, int, int, int]:
    bw = max(1, x2 - x1)
    bh = max(1, y2 - y1)
    pad_x = max(8, int(bw * pad_x_ratio))
    pad_y = max(8, int(bh * pad_y_ratio))
    x1 = max(0, x1 - pad_x)
    y1 = max(0, y1 - pad_y)
    x2 = min(w, x2 + pad_x)
    y2 = min(h, y2 + pad_y)
    return x1, y1, x2, y2


def _weighted_percentile(values, weights, q: float) -> float:
    if not values:
        return 0.0

    pairs = sorted(zip(values, weights), key=lambda item: item[0])
    total = sum(max(0.0, float(w)) for _, w in pairs)
    if total <= 0:
        idx = min(len(pairs) - 1, max(0, int(round((len(pairs) - 1) * q))))
        return float(pairs[idx][0])

    cutoff = total * min(1.0, max(0.0, q))
    acc = 0.0
    for value, weight in pairs:
        acc += max(0.0, float(weight))
        if acc >= cutoff:
            return float(value)
    return float(pairs[-1][0])


def _find_first_regex(text: str, patterns: Tuple[str, ...]) -> str:
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if not match:
            continue
        if match.groups():
            return match.group(1).strip()
        return match.group(0).strip()
    return ""


def _parse_amount_from_text(text: str) -> Optional[float]:
    candidates = re.findall(r"\d[\d.,]{1,20}", text or "")
    if not candidates:
        return None

    from validator import DataValidator

    parser = DataValidator()
    best = None
    for candidate in candidates:
        digits = re.sub(r"\D", "", candidate)
        if len(digits) < 3:
            continue
        value = parser._parse_number(candidate)
        if value <= 0:
            continue
        best = value
    return best
