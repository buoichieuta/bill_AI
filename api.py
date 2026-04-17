import io
import time
from pathlib import Path
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from typing import Dict, Any
from PIL import Image
from loguru import logger

from config import Config
from pipeline import GeminiInvoiceExtractor
from src.detector import get_detector, detect_invoice_region

# Khởi tạo ứng dụng FastAPI (Backend)
app = FastAPI(title="Invoice AI Backend for Android")

# Cấu hình CORS (Cho phép Mobile App gọi API mà không bị lỗi block)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Biến toàn cục chứa bộ trích xuất AI
_extractor = None
_detector = None

def get_extractor():
    global _extractor
    if _extractor is None:
        try:
             config = Config()
             _extractor = GeminiInvoiceExtractor(config)
             logger.info("✅ Đã tải mô hình AI vào RAM")
        except Exception as e:
             logger.error(f"❌ Lỗi tải mô hình: {e}")
    return _extractor

def get_detector_model():
    """Khởi tạo detector YOLO từ best.onnx"""
    global _detector
    if _detector is None:
        try:
            base_dir = Path(__file__).parent
            model_path = base_dir / "best.onnx"
            if not model_path.exists():
                logger.warning(f"⚠️ Không tìm thấy best.onnx tại {model_path}")
                return None
            _detector, error = get_detector(str(model_path))
            if error:
                logger.warning(f"⚠️ Lỗi tải detector: {error}")
                _detector = None
            else:
                logger.info("✅ Đã tải detector YOLO (best.onnx) vào RAM")
        except Exception as e:
            logger.error(f"❌ Lỗi khởi tạo detector: {e}")
            _detector = None
    return _detector

@app.on_event("startup")
async def startup_event():
    logger.info("Đang khởi động Server...")
    # Load mô hình vào RAM lúc khởi động để tăng tốc độ response
    logger.info("⏳ Đang load mô hình AI...")
    get_extractor()  
    get_detector_model()
    logger.info("✅ Mô hình đã sẵn sàng")

@app.get("/")
def read_root():
    return {"status": "ok", "message": "Invoice AI Backend is running. Sẵn sàng nhận ảnh từ Android."}

@app.post("/api/extract", response_model=Dict[str, Any])
async def extract_invoice(file: UploadFile = File(...)):
    """
    API Endponit: Nhận ảnh chụp từ điện thoại Android,
    gọi Gemini để trích xuất thông tin hóa đơn và trả về JSON.
    """
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File tải lên phải là ảnh (JPG/PNG)")
    
    try:
        # 1. Đọc dữ liệu ảnh từ request (luồng byte) gửi tới từ Android
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        
        extractor = get_extractor()
        if not extractor:
             raise HTTPException(status_code=500, detail="Hệ thống AI chưa sẵn sàng")

        # 2. Đưa ảnh vào quy trình AI xử lý
        logger.info(f"Đang xử lý ảnh: {file.filename} (Dung lượng: {len(contents)} bytes)")
        start_time = time.time()
        
        result = extractor.extract(image)
        
        elapsed = time.time() - start_time
        logger.info(f"Xử lý thành công sau {elapsed:.2f} giây")
        
        # 3. Trả về format JSON thẳng xuống Mobile
        return result
        
    except Exception as e:
        import traceback
        tb = traceback.format_exc()
        logger.error(f"Lỗi khi trích xuất: {e}\n{tb}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/extract-full", response_model=Dict[str, Any])
async def extract_invoice_full(file: UploadFile = File(...)):
    """
    API Endpoint: Quá trình trích xuất đầy đủ
    1. Detect vùng hóa đơn dùng YOLO (best.onnx)
    2. Trích xuất thông tin dùng Gemini
    """
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File tải lên phải là ảnh (JPG/PNG)")
    
    try:
        # Đọc ảnh gốc
        contents = await file.read()
        original_image = Image.open(io.BytesIO(contents)).convert("RGB")
        
        logger.info(f"Đang xử lý ảnh: {file.filename} (Dung lượng: {len(contents)} bytes)")
        start_time = time.time()
        
        # ========== BƯỚC 1: DETECT VÙNG HÓA ĐƠN (YOLO) ==========
        detector = get_detector_model()
        cropped_image = original_image
        detection_meta = None
        detection_error = None
        
        if detector:
            logger.info("Step 1/2: Detecting invoice region with YOLO...")
            cropped_image, detection_meta, detection_error = detect_invoice_region(
                original_image, detector
            )
            if detection_error:
                logger.warning(f"⚠️ Detection warning: {detection_error}")
            else:
                logger.info(f"✅ Detect thành công: bbox={detection_meta.get('detector_bbox')}")
        else:
            logger.info("⚠️ Detector không khả dụng, sử dụng ảnh gốc")
        
        # ========== BƯỚC 2: EXTRACT với GEMINI ==========
        logger.info("Step 2/2: Extracting data with Gemini...")
        extractor = get_extractor()
        if not extractor:
            raise HTTPException(status_code=500, detail="Hệ thống AI chưa sẵn sàng")
        
        result = extractor.extract(cropped_image)
        
        # ========== THÊM METADATA ==========
        if detection_meta:
            result['_detection'] = detection_meta
        if detection_error:
            result['_detection_error'] = detection_error
        
        elapsed = time.time() - start_time
        logger.info(f"✅ Xong — Dùng YOLO để detect + Gemini để extract ({elapsed:.2f}s)")
        
        return result
        
    except Exception as e:
        import traceback
        tb = traceback.format_exc()
        logger.error(f"Lỗi khi trích xuất (full pipeline): {e}\n{tb}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    # Khởi chạy server ở IP 0.0.0.0 để điện thoại cùng mạng Wifi (Local) có thể kết nối được
    # Port 8000
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=False)
