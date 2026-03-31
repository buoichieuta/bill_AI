from typing import Any, Dict, List, Optional, Tuple
from PIL import Image
from src.utils import (
    _weighted_percentile,
    _expand_box,
    DOCUMENT_DETECTOR_HINTS,
    FIELD_DETECTOR_HINTS,
    FIELD_ROLE_MAP,
    _bbox_iou
)

# NOTE: _get_detector_class_names and others were in app.py. 
# I should move them to utils.py or keep them in detector.py if they are specific to YOLO.
# Let's put them in detector.py for now as they are specific to the YOLO detector logic.

def _get_detector_class_names(detector) -> list[str]:
    names = getattr(getattr(detector, "model", None), "names", None) or {}
    if isinstance(names, dict):
        return [str(v) for v in names.values()]
    if isinstance(names, (list, tuple)):
        return [str(v) for v in names]
    return []


def _looks_like_field_detector(detector) -> bool:
    class_names = [x.lower() for x in _get_detector_class_names(detector)]
    if not class_names:
        return False

    has_document_class = any(name.strip() in DOCUMENT_DETECTOR_HINTS for name in class_names)
    has_field_class = any(any(hint in name for hint in FIELD_DETECTOR_HINTS) for name in class_names)
    return has_field_class and not has_document_class


def _collect_detection_candidates(boxes, detector, w: int, h: int):
    img_area = float(max(1, w * h))
    class_names = _get_detector_class_names(detector)
    candidates = []

    for i in range(len(boxes)):
        conf = float(boxes.conf[i].item())
        cls_id = int(boxes.cls[i].item()) if boxes.cls is not None else -1
        cls_name = class_names[cls_id] if 0 <= cls_id < len(class_names) else str(cls_id)
        x1f, y1f, x2f, y2f = boxes.xyxy[i].tolist()
        x1 = max(0, min(int(x1f), w - 1))
        y1 = max(0, min(int(y1f), h - 1))
        x2 = max(1, min(int(x2f), w))
        y2 = max(1, min(int(y2f), h))
        bw = max(1, x2 - x1)
        bh = max(1, y2 - y1)
        area_ratio = (bw * bh) / img_area
        candidates.append({
            "conf": conf,
            "cls_id": cls_id,
            "cls_name": cls_name,
            "x1": x1,
            "y1": y1,
            "x2": x2,
            "y2": y2,
            "area_ratio": area_ratio,
        })
    return candidates


def _pick_best_single_box(candidates):
    best = None
    for cand in candidates:
        score = cand["conf"] * 0.7 + min(1.0, cand["area_ratio"] / 0.5) * 0.3
        item = (score, cand)
        if best is None or item[0] > best[0]:
            best = item

    if best is None:
        return None, None

    cand = best[1]
    return (cand["x1"], cand["y1"], cand["x2"], cand["y2"]), {
        "detector_strategy": "single_box",
        "detector_boxes_used": 1,
        "detector_top_class": cand["cls_name"],
        "detector_score": round(cand["conf"], 4),
    }


def _build_invoice_envelope(candidates, w: int, h: int):
    confident = [c for c in candidates if c["conf"] >= 0.25]
    if len(confident) < 4:
        confident = [c for c in candidates if c["conf"] >= 0.15]
    if len(confident) < 4:
        confident = sorted(candidates, key=lambda c: c["conf"], reverse=True)[: min(len(candidates), 12)]
    if not confident:
        return None, None

    weights = [max(0.05, c["conf"]) for c in confident]
    x1 = int(_weighted_percentile([c["x1"] for c in confident], weights, 0.08))
    y1 = int(_weighted_percentile([c["y1"] for c in confident], weights, 0.06))
    x2 = int(_weighted_percentile([c["x2"] for c in confident], weights, 0.92))
    y2 = int(_weighted_percentile([c["y2"] for c in confident], weights, 0.94))

    if x2 <= x1 or y2 <= y1:
        x1 = min(c["x1"] for c in confident)
        y1 = min(c["y1"] for c in confident)
        x2 = max(c["x2"] for c in confident)
        y2 = max(c["y2"] for c in confident)

    x1, y1, x2, y2 = _expand_box(x1, y1, x2, y2, w, h, pad_x_ratio=0.12, pad_y_ratio=0.16)
    mean_conf = sum(c["conf"] for c in confident) / max(1, len(confident))
    return (x1, y1, x2, y2), {
        "detector_strategy": "field_envelope",
        "detector_boxes_used": len(confident),
        "detector_score": round(mean_conf, 4),
    }


def _canonical_field_name(cls_name: str) -> Optional[str]:
    clean = str(cls_name or "").upper().strip()
    if not clean or "PREFIX" in clean:
        return None
    return FIELD_ROLE_MAP.get(clean)


def _project_field_boxes(candidates, crop_box: Tuple[int, int, int, int]):
    cx1, cy1, cx2, cy2 = crop_box
    fields = []

    for cand in sorted(candidates, key=lambda item: item["conf"], reverse=True):
        field_name = _canonical_field_name(cand["cls_name"])
        if not field_name or cand["conf"] < 0.2:
            continue

        x1 = max(cx1, cand["x1"])
        y1 = max(cy1, cand["y1"])
        x2 = min(cx2, cand["x2"])
        y2 = min(cy2, cand["y2"])
        if x2 <= x1 or y2 <= y1:
            continue

        rel_box = [x1 - cx1, y1 - cy1, x2 - cx1, y2 - cy1]
        fields.append({
            "field": field_name,
            "label": cand["cls_name"],
            "confidence": round(float(cand["conf"]), 4),
            "bbox": rel_box,
            "bbox_source": [cand["x1"], cand["y1"], cand["x2"], cand["y2"]],
        })

    deduped = []
    for field in fields:
        is_duplicate = any(
            field["field"] == kept["field"] and _bbox_iou(field["bbox"], kept["bbox"]) >= 0.7
            for kept in deduped
        )
        if not is_duplicate:
            deduped.append(field)
    return deduped[:48]


def get_detector(model_path: str):
    """Khởi tạo detector best.pt (YOLO) nếu có."""
    try:
        from ultralytics import YOLO
        return YOLO(model_path), None
    except Exception as e:
        return None, f"Không load được {model_path}: {e}"


def detect_invoice_region(image: Image.Image, detector):
    """
    Dùng detector (YOLO) để tìm bbox hóa đơn và crop.
    """
    if detector is None:
        return image, None, "Detector is None"

    try:
        pred = detector.predict(source=image.convert("RGB"), verbose=False)
        if not pred:
            return image, None, "Detector không trả kết quả detect"

        boxes = pred[0].boxes
        if boxes is None or len(boxes) == 0:
            return image, None, "Detector không detect được vùng hóa đơn"

        w, h = image.size
        img_area = float(max(1, w * h))
        min_area_ratio = 0.08

        candidates = _collect_detection_candidates(boxes, detector, w, h)
        if not candidates:
            return image, None, "Detector không có bbox hợp lệ"

        is_field_detector = _looks_like_field_detector(detector)
        if is_field_detector:
            picked_box, meta = _build_invoice_envelope(candidates, w, h)
        else:
            picked_box, meta = _pick_best_single_box(candidates)

        if picked_box is None or meta is None:
            return image, None, "Detector không tạo được vùng crop hợp lệ"

        x1, y1, x2, y2 = picked_box
        bw = max(1, x2 - x1)
        bh = max(1, y2 - y1)
        area_ratio = (bw * bh) / img_area
        if area_ratio < min_area_ratio:
            return image, None, f"BBox từ detector quá nhỏ ({area_ratio:.3f}), dùng ảnh gốc"

        cropped = image.crop((x1, y1, x2, y2))
        field_boxes = _project_field_boxes(candidates, picked_box) if is_field_detector else []
        meta = {
            "detector": "YOLO",
            "detector_kind": "field" if is_field_detector else "document",
            "detector_area_ratio": round(area_ratio, 4),
            "detector_bbox": [x1, y1, x2, y2],
            "field_boxes": field_boxes,
            "field_box_count": len(field_boxes),
            **meta,
        }
        return cropped, meta, None
    except Exception as e:
        return image, None, f"Lỗi detect: {e}"
