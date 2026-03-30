import io
import time
import hashlib
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from PIL import Image, ImageOps, ImageEnhance, ImageFilter

# ─────────────────────────────────────────────
# Preprocessing Functions
# ─────────────────────────────────────────────

def _estimate_skew_angle(image: Image.Image) -> float:
    try:
        import cv2
        import numpy as np

        gray = np.array(image.convert("L"))
        gray = cv2.GaussianBlur(gray, (5, 5), 0)
        _, thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        coords = np.column_stack(np.where(thresh > 0))
        if len(coords) < 200:
            return 0.0

        angle = cv2.minAreaRect(coords)[-1]
        if angle < -45:
            angle = -(90 + angle)
        else:
            angle = -angle

        if abs(angle) < 0.25 or abs(angle) > 15:
            return 0.0
        return float(angle)
    except Exception:
        return 0.0


def _rotate_with_white_background(image: Image.Image, angle: float) -> Image.Image:
    if abs(angle) < 0.01:
        return image

    try:
        import cv2
        import numpy as np

        arr = np.array(image.convert("RGB"))
        h, w = arr.shape[:2]
        center = (w / 2.0, h / 2.0)
        matrix = cv2.getRotationMatrix2D(center, angle, 1.0)
        cos = abs(matrix[0, 0])
        sin = abs(matrix[0, 1])
        new_w = int((h * sin) + (w * cos))
        new_h = int((h * cos) + (w * sin))
        matrix[0, 2] += (new_w / 2.0) - center[0]
        matrix[1, 2] += (new_h / 2.0) - center[1]
        rotated = cv2.warpAffine(
            arr,
            matrix,
            (new_w, new_h),
            flags=cv2.INTER_CUBIC,
            borderMode=cv2.BORDER_CONSTANT,
            borderValue=(255, 255, 255),
        )
        return Image.fromarray(rotated)
    except Exception:
        return image.rotate(angle, expand=True, fillcolor="white")


def preprocess_receipt_image(image: Image.Image, extra_enhance: bool = False):
    """
    Tiền xử lý ảnh trước detect/OCR:
    xoay thẳng, denoise, tăng contrast, sharpen.
    """
    img = ImageOps.exif_transpose(image).convert("RGB")
    meta: Dict[str, Any] = {
        "preprocess_applied": True,
        "preprocess_steps": ["exif_transpose"],
    }

    angle = _estimate_skew_angle(img)
    if angle:
        img = _rotate_with_white_background(img, angle)
        meta["deskew_angle"] = round(angle, 2)
        meta["preprocess_steps"].append("deskew")

    try:
        import cv2
        import numpy as np

        gray = np.array(img.convert("L"))
        h_strength = 11 if extra_enhance else 8
        gray = cv2.fastNlMeansDenoising(gray, None, h_strength, 7, 21)
        if extra_enhance:
            gray = cv2.adaptiveThreshold(
                gray,
                255,
                cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                cv2.THRESH_BINARY,
                31,
                15,
            )
            meta["denoise"] = "fastnlmeans+adaptive_threshold"
        else:
            gray = cv2.equalizeHist(gray)
            meta["denoise"] = "fastnlmeans+equalize_hist"
        img = Image.fromarray(gray).convert("RGB")
        meta["preprocess_steps"].append("denoise")
    except Exception:
        gray = img.convert("L").filter(ImageFilter.MedianFilter(size=3))
        gray = ImageOps.autocontrast(gray, cutoff=1)
        img = gray.convert("RGB")
        meta["denoise"] = "median"
        meta["preprocess_steps"].append("denoise")

    img = ImageOps.autocontrast(img, cutoff=1)
    img = ImageEnhance.Contrast(img).enhance(1.2 if not extra_enhance else 1.35)
    img = img.filter(
        ImageFilter.UnsharpMask(
            radius=1.4,
            percent=150 if not extra_enhance else 180,
            threshold=2,
        )
    )
    img = ImageEnhance.Sharpness(img).enhance(1.15 if not extra_enhance else 1.28)
    meta["contrast_gain"] = 1.2 if not extra_enhance else 1.35
    meta["sharpness_gain"] = 1.15 if not extra_enhance else 1.28
    meta["preprocess_steps"].extend(["autocontrast", "sharpen"])
    return img, meta


def filter_for_gemini(image: Image.Image, extra_enhance: bool = False) -> Image.Image:
    """
    Pass cuối trước OCR/Gemini sau khi đã crop hóa đơn.
    """
    img = ImageOps.autocontrast(image.convert("L"), cutoff=1).convert("RGB")
    img = ImageEnhance.Contrast(img).enhance(1.22 if not extra_enhance else 1.38)
    img = img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=135 if not extra_enhance else 170, threshold=2))
    img = ImageEnhance.Sharpness(img).enhance(1.15 if not extra_enhance else 1.28)
    if extra_enhance:
        img = ImageEnhance.Brightness(img).enhance(1.03)
    return img


def save_intermediate_image(image: Image.Image, base_dir: Path, prefix: str = "for_gemini") -> str:
    """Lưu ảnh trung gian để debug pipeline detect/filter."""
    out_dir = base_dir / "outputs" / "intermediate"
    out_dir.mkdir(parents=True, exist_ok=True)

    buf = io.BytesIO()
    image.convert("RGB").save(buf, format="PNG")
    digest = hashlib.sha256(buf.getvalue()).hexdigest()[:10]
    ts = int(time.time() * 1000)
    path = out_dir / f"{prefix}_{ts}_{digest}.png"
    path.write_bytes(buf.getvalue())
    return str(path)
