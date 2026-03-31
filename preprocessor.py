"""
Xử lý và validate ảnh hóa đơn trước khi gửi API
"""
import os
from PIL import Image


class ImageProcessor:

    def validate_image(self, image_path: str) -> bool:
        """Kiểm tra ảnh hợp lệ"""
        valid_formats = ['.jpg', '.jpeg', '.png', '.bmp', '.tiff']
        if not any(image_path.lower().endswith(ext) for ext in valid_formats):
            print("❌ Định dạng ảnh không hỗ trợ.")
            return False

        try:
            with open(image_path, 'rb') as f:
                f.read()
        except Exception as e:
            print(f"❌ Không đọc được file: {e}")
            return False

        max_size = 10 * 1024 * 1024  # 10MB
        if os.path.getsize(image_path) > max_size:
            print("❌ File ảnh quá lớn (tối đa 10MB).")
            return False

        return True

    def resize_to_height(self, image: Image.Image, H: int) -> Image.Image:
        """Resize ảnh theo chiều cao"""
        width, height = image.size
        r = H / height
        return image.resize((int(width * r), H), Image.LANCZOS)

    def resize_image(self, image: Image.Image, size: tuple) -> Image.Image:
        """Resize ảnh theo kích thước cụ thể"""
        return image.resize(size, Image.LANCZOS)

    def enhance_image(self, image: Image.Image) -> Image.Image:
        """Tăng cường chất lượng ảnh"""
        from PIL import ImageEnhance
        image = ImageEnhance.Contrast(image).enhance(1.5)
        image = ImageEnhance.Sharpness(image).enhance(1.3)
        return image