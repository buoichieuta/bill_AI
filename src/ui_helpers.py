import re
import io
import csv
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from PIL import Image, ImageDraw
import gradio as gr

# ─────────────────────────────────────────────
# UI Constants and CSS
# ─────────────────────────────────────────────

CSS = """
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;600&family=Plus+Jakarta+Sans:wght@400;600;700&display=swap');

body, .gradio-container { background:#030712 !important; font-family:'Plus Jakarta Sans',sans-serif !important; }

.header { text-align:center; padding:28px 0 20px; border-bottom:1px solid #1e293b; margin-bottom:20px; }
.header h1 { font-size:26px; font-weight:700; color:#f8fafc; margin:0; letter-spacing:-0.5px; }
.header p  { color:#475569; font-size:13px; margin:4px 0 0; font-family:'IBM Plex Mono',monospace; }

button.primary { background:linear-gradient(135deg,#10b981,#059669) !important; border:none !important;
                  font-weight:700 !important; font-size:15px !important; height:46px !important; border-radius:10px !important; }
button.secondary { background:#1e293b !important; border:1px solid #334155 !important; color:#94a3b8 !important; border-radius:8px !important; }

.upload-zone { border:2px dashed #334155 !important; border-radius:12px !important; background:#0f172a !important; }
label { color:#94a3b8 !important; font-size:11px !important; font-weight:600 !important; text-transform:uppercase; letter-spacing:0.6px; }
textarea, input[type=text], input[type=password] {
    background:#0f172a !important; border-color:#334155 !important;
    color:#e2e8f0 !important; font-family:'IBM Plex Mono',monospace !important; font-size:12px !important; }
.gr-panel, .gr-box { background:#0f172a !important; border-color:#1e293b !important; }
.footer { text-align:center; color:#1e293b; font-size:11px; font-family:'IBM Plex Mono',monospace; padding:12px; }

/* Ẩn footer mặc định của Gradio */
footer { display: none !important; }
"""

PLACEHOLDER_HTML = '<div style="color:#475569;text-align:center;padding:32px;font-family:monospace">⬆️ Upload ảnh hóa đơn để bắt đầu</div>'

STATUS_IDLE_HTML = (
    '<div style="background:#0f172a;border:1px solid #1e293b;border-radius:12px;'
    'padding:14px 16px;font-family:\'IBM Plex Mono\',monospace;color:#64748b;font-size:12px">'
    'Trạng thái: chờ ảnh'
    '</div>'
)

# ─────────────────────────────────────────────
# UI Helper Functions
# ─────────────────────────────────────────────

def fmt_vnd(value) -> str:
    try:
        return f"{int(value):,} ₫".replace(",", ".")
    except Exception:
        return str(value) if value else "—"


def build_status_html(lines: List[str], state: str = "running") -> str:
    border = {
        "running": "#0ea5e9",
        "done": "#10b981",
        "error": "#ef4444",
        "idle": "#334155",
    }.get(state, "#334155")
    label = {
        "running": "Đang xử lý",
        "done": "Hoàn tất",
        "error": "Thất bại",
        "idle": "Chờ ảnh",
    }.get(state, "Trạng thái")

    body = "".join(f"<div style='padding:2px 0'>{line}</div>" for line in lines[-14:])
    if not body:
        body = "<div style='padding:2px 0;color:#64748b'>Chưa có log</div>"

    return (
        "<div style=\"background:#020617;border:1px solid {border};border-radius:12px;"
        "padding:14px 16px;font-family:'IBM Plex Mono',monospace;color:#cbd5e1;font-size:12px\">"
        "<div style=\"color:#f8fafc;font-weight:600;margin-bottom:8px\">{label}</div>"
        "<div style=\"max-height:190px;overflow:auto;line-height:1.55\">{body}</div>"
        "</div>"
    ).format(border=border, label=label, body=body)


def add_status_line(lines: List[str], message: str, started_at: float) -> None:
    elapsed = time.time() - started_at
    lines.append(f"[{elapsed:5.1f}s] {message}")


def _draw_bbox(draw: ImageDraw.Draw, bbox: List[int], label: str, colors: dict, w: int, h: int):
    """Vẽ một ô bao với nhãn dán phía trên."""
    if not bbox or len(bbox) != 4:
        return

    # Chuyển đổi tọa độ từ hệ 1000 sang pixel thực
    # Gemini: [ymin, xmin, ymax, xmax]
    ymin, xmin, ymax, xmax = bbox
    left = int(xmin * w / 1000)
    top = int(ymin * h / 1000)
    right = int(xmax * w / 1000)
    bottom = int(ymax * h / 1000)

    bg_color = colors.get("bg", (0, 255, 255, 200)) # Cyan mặc định
    border_color = colors.get("border", (255, 255, 255, 255))

    # 1. Vẽ khung
    draw.rectangle([left, top, right, bottom], outline=border_color, width=2)

    # 2. Vẽ nhãn (label tag)
    try:
        # Cố gắng load font, nếu không dùng mặc định
        font = None
    except:
        font = None

    label_h = 16
    draw.rectangle([left, top - label_h, left + len(label) * 8 + 4, top], fill=bg_color)
    draw.text((left + 3, top - label_h + 1), label, fill=(0, 0, 0, 255))


def build_overlay(image: Image.Image, data: dict) -> Image.Image:
    """Vẽ overlay nhãn chính xác theo tọa độ Gemini trả về (SELLER_BBOX, PRODUCT_BBOX...)."""
    try:
        img = image.copy().convert("RGBA")
        overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay)
        w, h = img.size

        # Định nghĩa bộ màu sắc cho từng loại trường
        field_colors = {
            "SELLER":     {"bg": (0, 255, 255, 220), "border": (245, 158, 11, 200)}, # Orange
            "ADDRESS":    {"bg": (0, 255, 255, 220), "border": (239, 68, 68, 180)},  # Red
            "TIMESTAMP":  {"bg": (0, 255, 255, 220), "border": (244, 63, 94, 180)},  # Pinkish
            "INVOICE_NO": {"bg": (0, 255, 255, 220), "border": (101, 163, 13, 200)}, # Green
            "PRODUCT":    {"bg": (0, 255, 255, 220), "border": (34, 197, 94, 160)},  # Green
            "NUM":        {"bg": (0, 255, 255, 220), "border": (59, 130, 246, 160)}, # Blue
            "UNIT_PRICE": {"bg": (0, 255, 255, 220), "border": (59, 130, 246, 160)}, # Blue
            "VALUE":      {"bg": (0, 255, 255, 220), "border": (59, 130, 246, 160)}, # Blue
            "TOTAL_COST": {"bg": (0, 255, 255, 220), "border": (30, 58, 138, 220)},  # Dark Blue
        }

        # 1. Vẽ các trường tổng quát (Global Fields)
        for field in ["SELLER", "ADDRESS", "TIMESTAMP", "INVOICE_NO", "TOTAL_COST"]:
            bbox = data.get(f"{field}_BBOX")
            if bbox:
                _draw_bbox(draw, bbox, field, field_colors.get(field, {}), w, h)

        # 2. Vẽ các trường trong danh sách sản phẩm
        products = data.get("PRODUCTS", [])
        for p in products:
            for field in ["PRODUCT", "NUM", "UNIT_PRICE", "VALUE"]:
                bbox = p.get(f"{field}_BBOX")
                if bbox:
                    _draw_bbox(draw, bbox, field, field_colors.get(field, {}), w, h)

        return Image.alpha_composite(img, overlay).convert("RGB")
    except Exception as e:
        from loguru import logger
        logger.error(f"❌ build_overlay lỗi: {e}")
        return image



def build_result_html(data: dict, elapsed: float) -> str:
    """HTML card hiển thị kết quả"""
    seller   = data.get("SELLER", "N/A")
    category = data.get("CATEGORY", "Khác")
    address  = data.get("ADDRESS", "")
    ts       = data.get("TIMESTAMP", "")
    inv_no   = data.get("INVOICE_NO", "")
    total    = data.get("TOTAL_COST", 0)
    cash     = data.get("CASH_RECEIVED")
    change   = data.get("CHANGE")
    products = data.get("PRODUCTS", [])
    meta     = data.get("_meta", {})

    model_lbl = meta.get("model", "?")
    fallback  = meta.get("fallback", False)
    parse_m   = meta.get("parse", "")
    detector  = meta.get("detector")
    detector_score = meta.get("detector_score")
    detector_err = meta.get("detector_error")
    preprocess_mode = meta.get("preprocess")
    processed_image_path = meta.get("processed_image_path")
    ocr_line_count = meta.get("ocr_line_count")
    field_box_count = meta.get("field_box_count")
    fb_badge  = ' <span style="color:#f59e0b;font-size:11px">⚡fallback</span>' if fallback else ""
    parse_badge = f' <span style="color:#94a3b8;font-size:11px">({parse_m})</span>' if parse_m != "direct" else ""
    if detector:
        det_badge = f' <span style="color:#22d3ee;font-size:11px">🎯 {detector} {detector_score or ""}</span>'
    elif detector_err:
        det_badge = f' <span style="color:#f59e0b;font-size:11px">🎯 {detector_err}</span>'
    else:
        det_badge = ""
    prep_badge = f' <span style="color:#67e8f9;font-size:11px">🧪 {preprocess_mode}</span>' if preprocess_mode else ""
    ocr_badge = f' <span style="color:#fbbf24;font-size:11px">🔎 {ocr_line_count} lines</span>' if ocr_line_count else ""
    field_badge = f' <span style="color:#a78bfa;font-size:11px">📦 {field_box_count} fields</span>' if field_box_count else ""
    file_row = f'<div style="color:#64748b;font-size:11px">🖼️ {processed_image_path}</div>' if processed_image_path else ""

    rows = ""
    for p in products:
        name  = p.get("PRODUCT", "")
        num   = p.get("NUM", 1)
        up    = p.get("UNIT_PRICE", 0)
        val   = p.get("VALUE", 0)
        up_td = f'<td style="padding:7px 10px;text-align:right;color:#94a3b8;font-size:12px">{fmt_vnd(up)}</td>' if up else '<td></td>'
        rows += (
            f'<tr style="border-bottom:1px solid #1e293b">'
            f'<td style="padding:7px 10px;color:#e2e8f0">{name}</td>'
            f'<td style="padding:7px 10px;text-align:center;color:#94a3b8">{num}</td>'
            f'{up_td}'
            f'<td style="padding:7px 10px;text-align:right;color:#34d399;font-weight:600">{fmt_vnd(val)}</td>'
            f'</tr>'
        )

    addr_row  = f'<div style="color:#64748b;font-size:12px">📍 {address}</div>' if address else ""
    inv_row   = f'<div style="color:#64748b;font-size:12px">📋 {inv_no}</div>' if inv_no else ""
    cash_row  = f'<div style="color:#94a3b8;font-size:13px">💵 Tiền khách: <b style="color:#e2e8f0">{fmt_vnd(cash)}</b></div>' if cash else ""
    change_row= f'<div style="color:#94a3b8;font-size:13px">↩️ Tiền thối: <b style="color:#fbbf24">{fmt_vnd(change)}</b></div>' if change else ""
    no_prod   = '<tr><td colspan="4" style="text-align:center;color:#475569;padding:12px">— Không trích xuất được sản phẩm —</td></tr>' if not products else ""

    return f"""
    <div style="font-family:'IBM Plex Mono',monospace;background:#0f172a;border-radius:12px;padding:20px;color:#e2e8f0">
      <div style="padding-bottom:12px;margin-bottom:14px;border-bottom:1px solid #1e293b">
        <div style="font-size:17px;font-weight:700;color:#f8fafc">🛒 {seller}</div>
        <div style="color:#94a3b8;font-size:13px">🏷️ Loại: {category}</div>
        {addr_row}{inv_row}{file_row}
        <div style="display:flex;gap:14px;margin-top:6px;flex-wrap:wrap;font-size:12px;color:#64748b">
          <span>🕒 {ts}</span>
          <span>🤖 {model_lbl}{fb_badge}{parse_badge}{det_badge}{prep_badge}{ocr_badge}{field_badge}</span>
          <span>⏱️ {elapsed:.1f}s</span>
          <span>📦 {len(products)} sản phẩm</span>
        </div>
      </div>
      <table style="width:100%;border-collapse:collapse;font-size:13px">
        <thead>
          <tr style="background:#1e293b">
            <th style="padding:7px 10px;text-align:left;color:#64748b;font-weight:500">Sản phẩm</th>
            <th style="padding:7px 10px;text-align:center;color:#64748b;font-weight:500">SL</th>
            <th style="padding:7px 10px;text-align:right;color:#64748b;font-weight:500">Đơn giá</th>
            <th style="padding:7px 10px;text-align:right;color:#64748b;font-weight:500">Thành tiền</th>
          </tr>
        </thead>
        <tbody>{rows}{no_prod}</tbody>
      </table>
      <div style="border-top:1px solid #334155;margin-top:10px;padding-top:10px">
        {cash_row}{change_row}
        <div style="font-size:16px;font-weight:700;color:#10b981;margin-top:6px">
          💰 TỔNG CỘNG: {fmt_vnd(total)}
        </div>
      </div>
    </div>"""


def generate_csv_string(data: dict) -> str:
    """Tạo chuỗi CSV cho hóa đơn hiện tại để hiển thị trên UI"""
    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow([
        "Người bán", "Loại", "Địa chỉ", "Mã số thuế", "Số hóa đơn", "Thời gian",
        "Tên sản phẩm", "Số lượng", "Đơn giá", "Thành tiền",
        "Tổng cộng", "Tiền khách đưa", "Tiền thối"
    ])
    products = data.get("PRODUCTS", [])
    if not products:
        products = [{}]

    for p in products:
        writer.writerow([
            data.get("SELLER", ""), data.get("CATEGORY", ""), data.get("ADDRESS", ""), data.get("TAX_CODE", ""),
            data.get("INVOICE_NO", ""), data.get("TIMESTAMP", ""),
            p.get("PRODUCT", ""), p.get("NUM", ""), p.get("UNIT_PRICE", ""), p.get("VALUE", ""),
            data.get("TOTAL_COST", ""), data.get("CASH_RECEIVED", ""), data.get("CHANGE", "")
        ])
    return output.getvalue()


def save_csv(data: dict, base_dir: Path) -> str:
    """Lưu kết quả ra file CSV chuẩn chuyên nghiệp theo tên người bán"""
    if not data:
        return None
    
    seller = data.get("SELLER", "") or "Unknown_Seller"
    safe_seller = re.sub(r'[\\/*?:"<>|]', "", seller).strip()
    if not safe_seller:
        safe_seller = "Unknown_Seller"
        
    out_dir = base_dir / "outputs" / "cvs"
    out_dir.mkdir(parents=True, exist_ok=True)
    
    inv_no = data.get("INVOICE_NO") or ""
    safe_inv = re.sub(r'[\\/*?:"<>|]', "", str(inv_no)).strip()
    unique_id = str(int(time.time()))
    
    if safe_inv:
        file_name = f"{safe_seller}_{safe_inv}_{unique_id}.csv"
    else:
        file_name = f"{safe_seller}_{unique_id}.csv"
        
    file_path = out_dir / file_name
    
    products = data.get("PRODUCTS", [])
    if not products:
        products = [{}]
        
    with open(file_path, mode="w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "Người bán", "Loại", "Địa chỉ", "Mã số thuế", "Số hóa đơn", "Thời gian",
            "Tên sản phẩm", "Số lượng", "Đơn giá", "Thành tiền",
            "Tổng cộng", "Tiền khách đưa", "Tiền thối"
        ])
            
        for p in products:
            writer.writerow([
                data.get("SELLER", ""), data.get("CATEGORY", ""), data.get("ADDRESS", ""), data.get("TAX_CODE", ""),
                data.get("INVOICE_NO", ""), data.get("TIMESTAMP", ""),
                p.get("PRODUCT", ""), p.get("NUM", ""), p.get("UNIT_PRICE", ""), p.get("VALUE", ""),
                data.get("TOTAL_COST", ""), data.get("CASH_RECEIVED", ""), data.get("CHANGE", "")
            ])
            
    return str(file_path)
