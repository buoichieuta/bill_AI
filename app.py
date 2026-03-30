"""
Gradio UI cho Invoice AI (Dàn trang Modular)
Chạy: python app.py
"""
import os
import time
import traceback
import hashlib
import copy
import io
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import gradio as gr
from PIL import Image
from loguru import logger
from dotenv import load_dotenv

# Import từ các module mới
from src.utils import (
    _safe_int,
    _normalize_bbox,
    _bbox_iou,
    _bbox_overlap_ratio,
    _expand_box,
    _weighted_percentile,
    _find_first_regex,
    _parse_amount_from_text,
    _normalized_text,
)
from src.preprocessor import (
    preprocess_receipt_image,
    filter_for_gemini,
    save_intermediate_image,
)
from src.detector import (
    get_detector,
    detect_invoice_region,
)
from src.extractor import (
    get_extractor,
)
from src.ui_helpers import (
    fmt_vnd,
    build_overlay,
    build_result_html,
    build_status_html,
    add_status_line,
    generate_csv_string,
    save_csv,
    CSS,
    PLACEHOLDER_HTML,
    STATUS_IDLE_HTML,
)

# ─────────────────────────────────────────────
# Bootstrap & Global State
# ─────────────────────────────────────────────
BASE_DIR = Path(__file__).parent
_extractor = None
_extractor_signature = None
_detector = None
_result_cache = {}

load_dotenv()

def get_shared_extractor(api_key: str = "", model: str = "gemini-flash-latest"):
    global _extractor, _extractor_signature
    key = (api_key or "").strip() or os.getenv("GEMINI_API_KEY", "")
    if not key:
        return None, "❌ Chưa có GEMINI_API_KEY. Nhập vào ô Config hoặc tạo file .env"

    try:
        signature = (key, model)
        if _extractor is not None and _extractor_signature == signature:
            return _extractor, None

        _extractor = get_extractor(api_key=key, model=model)
        _extractor_signature = signature
        return _extractor, None
    except Exception as e:
        return None, f"❌ Khởi tạo thất bại: {e}\n{traceback.format_exc()}"


def get_shared_detector():
    global _detector
    if _detector is not None:
        return _detector, None

    model_path = BASE_DIR / "best.pt"
    if not model_path.exists():
        return None, "Không tìm thấy best.pt"

    detector, err = get_detector(str(model_path))
    if detector:
        _detector = detector
    return _detector, err


def make_image_cache_key(
    image: Image.Image,
    model_choice: str,
    enhance: bool,
    use_local_model: bool,
    fast_mode: bool,
) -> str:
    rgb = image.convert("RGB")
    buf = io.BytesIO()
    rgb.save(buf, format="PNG")
    h = hashlib.sha256()
    h.update(buf.getvalue())
    h.update(model_choice.encode("utf-8"))
    h.update(str(bool(enhance)).encode("utf-8"))
    h.update(str(bool(use_local_model)).encode("utf-8"))
    h.update(str(bool(fast_mode)).encode("utf-8"))
    return h.hexdigest()


# ─────────────────────────────────────────────
# Main handler
# ─────────────────────────────────────────────

def process_invoice_stream(
    image,
    progress=gr.Progress(track_tqdm=False)
):
    overlay_img = None
    result_html = PLACEHOLDER_HTML
    csv_str = ""
    download_update = gr.update(visible=False, value=None)
    status_lines: List[str] = []
    started_at = time.time()

    def snapshot(state: str = "running"):
        return overlay_img, result_html, csv_str, download_update, build_status_html(status_lines, state)

    if image is None:
        yield overlay_img, result_html, csv_str, download_update, STATUS_IDLE_HTML
        return

    progress(0.02, desc="Đã nhận ảnh")
    add_status_line(status_lines, f"Đã nhận ảnh {image.size[0]}x{image.size[1]}", started_at)
    yield snapshot("running")

    add_status_line(status_lines, "Khởi tạo extractor", started_at)
    progress(0.08, desc="Khởi tạo model")
    yield snapshot("running")
    extractor, err = get_shared_extractor()
    if err:
        result_html = f'<div style="color:#ef4444;padding:16px;font-family:monospace">{err}</div>'
        add_status_line(status_lines, f"Lỗi khởi tạo extractor: {err}", started_at)
        yield overlay_img, result_html, csv_str, download_update, build_status_html(status_lines, "error")
        return

    try:
        add_status_line(status_lines, "Preprocess ảnh: deskew + denoise + sharpen", started_at)
        progress(0.16, desc="Preprocess ảnh")
        yield snapshot("running")
        preprocessed_image, preprocess_meta = preprocess_receipt_image(image, extra_enhance=False)
        image_for_extract = preprocessed_image
        detector_meta = None
        detector_err = None
        field_boxes = []
        preprocess_steps = ",".join(preprocess_meta.get("preprocess_steps", []))
        add_status_line(status_lines, f"Preprocess xong: {preprocess_steps}", started_at)
        yield snapshot("running")

        add_status_line(status_lines, "Đang chạy best.pt để tìm vùng hóa đơn", started_at)
        progress(0.30, desc="Chạy best.pt")
        yield snapshot("running")
        detector, d_err = get_shared_detector()
        if detector:
            image_for_extract, detector_meta, detector_err = detect_invoice_region(preprocessed_image, detector)
            if detector_meta:
                det_kind = detector_meta.get("detector_kind", "?")
                add_status_line(
                    status_lines,
                    f"best.pt hoạt động: kind={det_kind}, bbox={detector_meta.get('detector_bbox')}",
                    started_at,
                )
            else:
                add_status_line(status_lines, f"best.pt không dùng được: {detector_err}", started_at)
        else:
            detector_err = d_err
            add_status_line(status_lines, f"best.pt không load được: {detector_err}", started_at)
        
        preprocess_mode = "preprocess->detect->filter->gemini"
        yield snapshot("running")

        add_status_line(status_lines, "Filter ảnh trước Gemini", started_at)
        progress(0.40, desc="Filter ảnh")
        yield snapshot("running")
        image_for_extract = filter_for_gemini(image_for_extract, extra_enhance=False)

        cache_key = make_image_cache_key(
            image_for_extract,
            "gemini-2.5-flash",
            False,
            True,
            True,
        )
        if cache_key in _result_cache:
            cached = copy.deepcopy(_result_cache[cache_key])
            cached_meta = cached.setdefault("_meta", {})
            cached_meta.update(preprocess_meta)
            if detector_meta:
                cached_meta.update(detector_meta)
            elif detector_err:
                cached_meta["detector_error"] = detector_err
            cached_meta["preprocess"] = preprocess_mode
            elapsed = time.time() - started_at
            overlay_img = build_overlay(image_for_extract, cached)
            result_html = build_result_html(cached, elapsed)
            csv_str = generate_csv_string(cached)
            download_update = gr.update(visible=True, value=save_csv(cached, BASE_DIR))
            add_status_line(status_lines, "Cache hit: bỏ qua OCR/Gemini, trả kết quả ngay", started_at)
            progress(1.0, desc="Hoàn tất từ cache")
            yield snapshot("done")
            return

        add_status_line(status_lines, "Lưu ảnh trung gian để debug pipeline", started_at)
        processed_image_path = save_intermediate_image(
            image_for_extract,
            BASE_DIR,
            prefix="detected_filtered"
        )
        add_status_line(status_lines, f"Ảnh trung gian: {processed_image_path}", started_at)
        yield snapshot("running")

        add_status_line(status_lines, "Gửi ảnh trực tiếp cho Gemini", started_at)
        progress(0.64, desc="Gọi Gemini")
        yield snapshot("running")

        result = extractor.extract(image_for_extract)

        result_meta = result.setdefault("_meta", {})
        result_meta.update(preprocess_meta)
        if detector_meta:
            result_meta.update(detector_meta)
        elif detector_err:
            result_meta["detector_error"] = detector_err
        result_meta["preprocess"] = preprocess_mode
        result_meta["processed_image_path"] = processed_image_path

        _result_cache[cache_key] = copy.deepcopy(result)
        elapsed = time.time() - started_at
        overlay_img = build_overlay(image_for_extract, result)
        result_html = build_result_html(result, elapsed)
        csv_str = generate_csv_string(result)
        download_update = gr.update(visible=True, value=save_csv(result, BASE_DIR))
        add_status_line(status_lines, f"Hoàn tất: {len(result.get('PRODUCTS', []))} sản phẩm", started_at)
        progress(1.0, desc="Hoàn tất")
        yield snapshot("done")

    except Exception as e:
        elapsed = time.time() - started_at
        tb = traceback.format_exc()
        logger.error(f"❌ {e}\n{tb}")
        result_html = (
            f'<div style="background:#1e0a0a;border:1px solid #7f1d1d;border-radius:8px;'
            f'padding:16px;font-family:monospace">'
            f'<div style="color:#ef4444;font-weight:700">❌ Thất bại ({elapsed:.1f}s)</div>'
            f'<div style="color:#fca5a5;font-size:13px;margin-top:6px">{str(e)}</div>'
            f'</div>'
        )
        add_status_line(status_lines, f"Lỗi pipeline: {e}", started_at)
        yield overlay_img, result_html, csv_str, download_update, build_status_html(status_lines, "error")


def clear_all():
    return None, None, PLACEHOLDER_HTML, "", gr.update(visible=False, value=None), STATUS_IDLE_HTML


# ─────────────────────────────────────────────
# UI
# ─────────────────────────────────────────────

def build_ui():
    with gr.Blocks(title="Invoice AI 🧾") as demo:

        gr.HTML('<div class="header"><h1>🧾 Invoice AI</h1><p>Nhận diện hóa đơn Việt Nam · Powered by Gemini Vision</p></div>')


        # Main
        with gr.Row(equal_height=False):

            # Left — input
            with gr.Column(scale=1):
                img_input = gr.Image(label="📸 Ảnh hóa đơn", type="pil",
                                     elem_classes="upload-zone", height=380)
                with gr.Row():
                    btn_submit = gr.Button("🚀 Trích xuất", variant="primary", scale=3)
                    btn_clear  = gr.Button("🗑️ Xóa", variant="secondary", scale=1)

            # Right — output
            with gr.Column(scale=1):
                status_html = gr.HTML(value=STATUS_IDLE_HTML)
                with gr.Tabs():
                    with gr.TabItem("📊 Kết quả"):
                        result_html_comp = gr.HTML(value=PLACEHOLDER_HTML)
                    with gr.TabItem("🖼️ Overlay"):
                        overlay_out = gr.Image(label="Vùng nhận diện", type="pil",
                                               interactive=False, height=380)
                    with gr.TabItem("📋 CSV"):
                        csv_out = gr.Textbox(label="CSV Output", lines=22, max_lines=22)

                with gr.Row():
                    btn_download = gr.DownloadButton(
                        label="⬇️ Tải CSV", visible=False,
                        variant="secondary"
                    )

        gr.HTML('<div class="footer">JPG · PNG · BMP · tối đa 10MB · hóa đơn siêu thị, nhà hàng, cafe</div>')

        # ── Events ──
        def on_image_change(img):
            if img is None:
                return STATUS_IDLE_HTML
            lines = [f"[  0.0s] Ảnh đã tải lên: {img.size[0]}x{img.size[1]}", "[  0.0s] Sẵn sàng bấm Trích xuất"]
            return build_status_html(lines, "idle")

        btn_submit.click(
            fn=process_invoice_stream,
            inputs=[img_input],
            outputs=[overlay_out, result_html_comp, csv_out, btn_download, status_html],
        )

        img_input.change(
            fn=on_image_change,
            inputs=[img_input],
            outputs=[status_html],
        )

        btn_clear.click(
            fn=clear_all,
            outputs=[img_input, overlay_out, result_html_comp, csv_out, btn_download, status_html],
        )

    return demo


# ─────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────
if __name__ == "__main__":
    import sys
    if sys.stdout.encoding.lower() != 'utf-8':
        sys.stdout.reconfigure(encoding='utf-8')

    import argparse

    p = argparse.ArgumentParser()
    p.add_argument("--host",    default="0.0.0.0")
    p.add_argument("--port",    type=int, default=7860)
    p.add_argument("--share",   action="store_true")
    p.add_argument("--api-key", default="")
    args = p.parse_args()

    if args.api_key:
        os.environ["GEMINI_API_KEY"] = args.api_key

    print("=" * 50)
    print("🚀 Invoice AI (Modular) đang khởi động...")
    print(f"📍 http://localhost:{args.port}")
    print("=" * 50)

    build_ui().queue(default_concurrency_limit=2).launch(
        server_name=args.host,
        server_port=args.port,
        share=args.share,
        css=CSS,
        theme=gr.themes.Base(primary_hue="emerald", neutral_hue="slate"),
    )
