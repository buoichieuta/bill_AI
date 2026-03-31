import io
import os
import traceback

from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from dotenv import load_dotenv
from pathlib import Path

# Import từ thư viện nội bộ
from src.extractor import get_extractor
from src.preprocessor import preprocess_receipt_image, filter_for_gemini
from src.detector import get_detector, detect_invoice_region

load_dotenv()
BASE_DIR = Path(__file__).parent

# ==========================================
# 1. KHỞI TẠO MÁY CHỦ BỘ NÃO (FASTAPI)
# ==========================================
app = FastAPI(title="Invoice AI Android API")

# Cho phép điện thoại truy cập API không bị chặn CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

_extractor = None
_detector = None

def get_shared_extractor():
    global _extractor
    if _extractor:
        return _extractor
    api_key = os.getenv("GEMINI_API_KEY", "")
    if not api_key:
        raise ValueError("Chưa cấu hình GEMINI_API_KEY trong file .env")
    # Khởi tạo 1 con Gemini mặc định
    _extractor = get_extractor(api_key=api_key, model="gemini-flash-latest")
    return _extractor

def get_shared_detector():
    global _detector
    if _detector:
        return _detector
    model_path = BASE_DIR / "best.pt"
    if model_path.exists():
        _detector, _ = get_detector(str(model_path))
    return _detector

@app.on_event("startup")
async def startup_event():
    print("🌍 Máy chủ đang khởi động...")
    print("🤖 Đang nạp Model YOLO & Gemini vào RAM...")
    try:
        get_shared_extractor()
        get_shared_detector()
        print("✅ Máy chủ SẴN SÀNG nhận ảnh từ Điện thoại!")
    except Exception as e:
        print(f"⚠️ Chưa thể nạp AI (Chưa có .env hoặc best.pt?): {e}")

# ==========================================
# 2. ĐỊA CHỈ API ĐỂ ĐIỆN THOẠI GỬI ẢNH TỚI
# ==========================================
@app.post("/api/extract")
async def extract_invoice(
    # Điện thoại sẽ đóng gói ảnh vào field tên là "file"
    file: UploadFile = File(...),
    use_local_model: bool = Form(True)
):
    print(f"📸 Vừa nhận 1 ảnh từ điện thoại: {file.filename}")
    try:
        # A. Đọc luồng dữ liệu nhị phân thành ảnh PIL
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        
        # B. Tiền xử lý (Cắt, chỉnh sáng)
        enhanced_image, _ = preprocess_receipt_image(image, extra_enhance=False)
        image_for_extract = enhanced_image
        
        # C. Kéo khung vuông (Bounding Box) bằng YOLO if requested
        if use_local_model:
            detector = get_shared_detector()
            if detector:
                image_for_extract, _, _ = detect_invoice_region(enhanced_image, detector)
        
        image_for_extract = filter_for_gemini(image_for_extract, extra_enhance=False)

        # D. Gửi ảnh lên Google Gemini đọc Text JSON
        extractor = get_shared_extractor()
        result = extractor.extract(image_for_extract)
        
        print("✅ Phân tích xong! Trả kết quả JSON về Điện thoại.")
        
        # E. Trả về điện thoại
        return {
            "success": True,
            "data": result
        }

    except Exception as e:
        print("❌ Có lỗi xảy ra!")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    # Chạy ở mọi địa chỉ IP máy tính với cổng 8000
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=True)
