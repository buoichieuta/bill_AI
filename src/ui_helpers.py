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


def build_overlay(image: Image.Image, data: dict) -> Image.Image:
    """Vẽ overlay gọn theo block dữ liệu sau khi đã extract xong."""
    try:
        img = image.copy().convert("RGBA")
        overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay)
        w, h = img.size
        meta = data.get("_meta", {}) or {}

        # Tìm vùng hóa đơn sáng để đặt tag.
        gray = image.convert("L")
        px = gray.load()
        min_x, min_y, max_x, max_y = w, h, 0, 0
        bright_threshold = 150
        for y in range(0, h, 2):
            for x in range(0, w, 2):
                if px[x, y] >= bright_threshold:
                    if x < min_x:
                        min_x = x
                    if y < min_y:
                        min_y = y
                    if x > max_x:
                        max_x = x
                    if y > max_y:
                        max_y = y
        if min_x >= max_x or min_y >= max_y:
            min_x, min_y, max_x, max_y = int(w * 0.18), int(h * 0.08), int(w * 0.82), int(h * 0.92)
        else:
            pad_x = int((max_x - min_x) * 0.05)
            pad_y = int((max_y - min_y) * 0.03)
            min_x = max(0, min_x - pad_x)
            min_y = max(0, min_y - pad_y)
            max_x = min(w - 1, max_x + pad_x)
            max_y = min(h - 1, max_y + pad_y)

        rw = max_x - min_x
        rh = max_y - min_y

        draw.rectangle([(min_x, min_y), (max_x, max_y)], outline=(59, 130, 246, 120), width=2)

        def draw_tag(x, y, label, value, color_bg, color_border, max_chars=52):
            if value is None:
                return y
            txt = str(value).strip()
            if not txt:
                return y
            txt = re.sub(r"\s+", " ", txt)
            if len(txt) > max_chars:
                txt = txt[:max_chars - 1] + "…"
            content = f"{label}: {txt}"
            tag_w = min(int(rw * 0.92), max(110, 7 * len(content)))
            tag_h = 20
            x2 = min(max_x - 2, x + tag_w)
            y2 = min(max_y - 2, y + tag_h)
            draw.rectangle([(x, y), (x2, y2)], fill=color_bg, outline=color_border, width=2)
            draw.text((x + 4, y + 3), content, fill=(245, 247, 255, 255))
            return y2 + 4

        # Header tags ở góc phải phần đầu hóa đơn.
        y_header = min_y + int(rh * 0.03)
        x_header = min_x + int(rw * 0.56)
        y_header = draw_tag(x_header, y_header, "SELLER", data.get("SELLER"), (37, 99, 235, 170), (59, 130, 246, 245), 40)
        y_header = draw_tag(x_header, y_header, "ADDRESS", data.get("ADDRESS"), (76, 29, 149, 165), (147, 51, 234, 245), 45)
        draw_tag(x_header, y_header, "TIMESTAMP", data.get("TIMESTAMP"), (22, 163, 74, 160), (34, 197, 94, 240), 24)

        # Products tags ở block giữa, tách PRODUCT/NUM/VALUE thành ô riêng.
        products = data.get("PRODUCTS", []) or []
        prod_start_y = min_y + int(rh * 0.40)
        y_prod = prod_start_y
        row_h = max(22, int(rh * 0.045))
        x_prod = min_x + int(rw * 0.03)
        x_num = min_x + int(rw * 0.68)
        x_val = min_x + int(rw * 0.80)

        for i, p in enumerate(products[:8], start=1):
            pname = re.sub(r"\s+", " ", str(p.get("PRODUCT", "")).strip())
            pname = re.sub(r"\b\d{1,2}[:/]\d{1,2}([:/]\d{2,4})?\b", "", pname).strip()
            pnum = p.get("NUM", 1)
            pval = p.get("VALUE", 0)

            draw_tag(x_prod, y_prod, "PRODUCT", f"{i}. {pname}", (8, 145, 178, 145), (34, 211, 238, 225), 30)
            draw_tag(x_num, y_prod, "NUM", pnum, (20, 83, 45, 150), (74, 222, 128, 230), 6)
            draw_tag(x_val, y_prod, "VALUE", pval, (180, 83, 9, 150), (249, 115, 22, 230), 10)

            y_prod += row_h
            if y_prod > min_y + int(rh * 0.74):
                break

        # Total tag ở gần cuối hóa đơn.
        total_y = min_y + int(rh * 0.76)
        x_total = min_x + int(rw * 0.56)
        draw_tag(x_total, total_y, "TOTAL_COST", data.get("TOTAL_COST"), (180, 83, 9, 170), (249, 115, 22, 245), 16)

        field_boxes = meta.get("field_boxes") or []
        highlighted = []
        priority_fields = {"seller", "address", "timestamp", "invoice_no", "total", "cash_received", "change"}
        for field in field_boxes:
            if field.get("field") in priority_fields:
                highlighted.append(field)
        product_fields = [field for field in field_boxes if field.get("field") == "product_name"][:6]
        highlighted.extend(product_fields)

        for field in highlighted[:14]:
            bbox = field.get("bbox")
            if not bbox:
                continue
            x1, y1, x2, y2 = bbox
            draw.rectangle([(x1, y1), (x2, y2)], outline=(250, 204, 21, 200), width=2)
            label = str(field.get("field", field.get("label", ""))).upper()
            label = label[:18]
            tag_y = max(0, y1 - 16)
            tag_x2 = min(w - 1, x1 + max(54, 7 * len(label)))
            draw.rectangle([(x1, tag_y), (tag_x2, min(h - 1, tag_y + 14))], fill=(250, 204, 21, 200))
            draw.text((x1 + 3, tag_y + 1), label, fill=(15, 23, 42, 255))

        return Image.alpha_composite(img, overlay).convert("RGB")
    except Exception:
        return image


def build_result_html(data: dict, elapsed: float) -> str:
    """HTML card hiển thị kết quả"""
    seller   = data.get("SELLER", "N/A")
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
        "Người bán", "Địa chỉ", "Mã số thuế", "Số hóa đơn", "Thời gian",
        "Tên sản phẩm", "Số lượng", "Đơn giá", "Thành tiền",
        "Tổng cộng", "Tiền khách đưa", "Tiền thối"
    ])
    products = data.get("PRODUCTS", [])
    if not products:
        products = [{}]

    for p in products:
        writer.writerow([
            data.get("SELLER", ""), data.get("ADDRESS", ""), data.get("TAX_CODE", ""),
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
            "Người bán", "Địa chỉ", "Mã số thuế", "Số hóa đơn", "Thời gian",
            "Tên sản phẩm", "Số lượng", "Đơn giá", "Thành tiền",
            "Tổng cộng", "Tiền khách đưa", "Tiền thối"
        ])
            
        for p in products:
            writer.writerow([
                data.get("SELLER", ""), data.get("ADDRESS", ""), data.get("TAX_CODE", ""),
                data.get("INVOICE_NO", ""), data.get("TIMESTAMP", ""),
                p.get("PRODUCT", ""), p.get("NUM", ""), p.get("UNIT_PRICE", ""), p.get("VALUE", ""),
                data.get("TOTAL_COST", ""), data.get("CASH_RECEIVED", ""), data.get("CHANGE", "")
            ])
            
    return str(file_path)
