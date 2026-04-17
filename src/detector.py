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

def _get_detector_class_names(detector) -> list[str]:
    names = getattr(getattr(detector, "model", None), "names", None) or {}
    if isinstance(names, dict):
        return [str(v) for v in names.values()]
    if isinstance(names, (list, tuple)):
        return [str(v) for v in names]
    return []


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


def _merge_all_boxes(candidates, img_width: int, img_height: int):
    """
    Merge all detected field boxes into one bounding box.
    This method encompasses all detected fields with padding.
    """
    if not candidates:
        return None, None
    
    # Find bounds of all boxes
    all_x1 = [c["x1"] for c in candidates]
    all_y1 = [c["y1"] for c in candidates]
    all_x2 = [c["x2"] for c in candidates]
    all_y2 = [c["y2"] for c in candidates]
    
    merged_x1 = min(all_x1)
    merged_y1 = min(all_y1)
    merged_x2 = max(all_x2)
    merged_y2 = max(all_y2)
    
    # Add padding (5% of image dimensions)
    padding_x = int(img_width * 0.05)
    padding_y = int(img_height * 0.05)
    
    merged_x1 = max(0, merged_x1 - padding_x)
    merged_y1 = max(0, merged_y1 - padding_y)
    merged_x2 = min(img_width, merged_x2 + padding_x)
    merged_y2 = min(img_height, merged_y2 + padding_y)
    
    return (merged_x1, merged_y1, merged_x2, merged_y2), {
        "detector_strategy": "merged_fields",
        "detector_boxes_used": len(candidates),
        "detector_top_class": "INVOICE_REGION",
        "detector_score": round(sum(c["conf"] for c in candidates) / len(candidates), 4),
    }


def get_detector(model_path: str):
    """Khởi tạo detector best.onnx (YOLO) nếu có."""
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
        min_area_ratio = 0.04

        candidates = _collect_detection_candidates(boxes, detector, w, h)
        if not candidates:
            return image, None, "Detector không có bbox hợp lệ"

        picked_box, meta = _merge_all_boxes(candidates, w, h)

        if picked_box is None or meta is None:
            return image, None, "Detector không tạo được vùng crop hợp lệ"

        x1, y1, x2, y2 = picked_box
        bw = max(1, x2 - x1)
        bh = max(1, y2 - y1)
        area_ratio = (bw * bh) / img_area
        if area_ratio < min_area_ratio:
            return image, None, f"BBox từ detector quá nhỏ ({area_ratio:.3f}), dùng ảnh gốc"

        cropped = image.crop((x1, y1, x2, y2))
        meta = {
            "detector": "YOLO",
            "detector_kind": "document",
            "detector_area_ratio": round(area_ratio, 4),
            "detector_bbox": [x1, y1, x2, y2],
            **meta,
        }
        return cropped, meta, None
    except Exception as e:
        return image, None, f"Lỗi detect: {e}"
