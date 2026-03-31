"""
Validate và normalize dữ liệu hóa đơn trích xuất từ Gemini
"""
import re
from typing import Dict, List, Any, Union
from datetime import datetime


class DataValidator:

    def validate_json_structure(self, data: Dict[str, Any]) -> bool:
        """Kiểm tra JSON có đủ field bắt buộc"""
        required_keys = {"SELLER", "TIMESTAMP", "PRODUCTS", "TOTAL_COST"}
        if not isinstance(data, dict):
            return False
        if not required_keys.issubset(data.keys()):
            missing = required_keys - data.keys()
            print(f"⚠️ Thiếu field: {missing}")
            return False
        if not isinstance(data["PRODUCTS"], list):
            print("⚠️ PRODUCTS phải là list")
            return False
        return True

    def validate_field_types(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Đảm bảo kiểu dữ liệu đúng, tự sửa nếu có thể"""
        try:
            # SELLER
            if not isinstance(data.get("SELLER"), str):
                data["SELLER"] = str(data.get("SELLER", ""))

            # TOTAL_COST
            total = data.get("TOTAL_COST")
            if isinstance(total, str):
                data["TOTAL_COST"] = self._parse_number(total)
            elif isinstance(total, (int, float)):
                data["TOTAL_COST"] = float(total)
            else:
                data["TOTAL_COST"] = 0.0

            # CASH_RECEIVED & CHANGE (optional)
            for field in ["CASH_RECEIVED", "CHANGE", "SUBTOTAL", "TAX"]:
                val = data.get(field)
                if val is not None:
                    data[field] = self._parse_number(val) if isinstance(val, str) else float(val)

            # PRODUCTS
            for prod in data.get("PRODUCTS", []):
                if not isinstance(prod, dict):
                    continue

                # NUM
                num = prod.get("NUM")
                if isinstance(num, str):
                    prod["NUM"] = int(re.sub(r"[^\d]", "", num) or "1")
                elif isinstance(num, (int, float)):
                    prod["NUM"] = int(num)
                else:
                    prod["NUM"] = 1

                # VALUE
                val = prod.get("VALUE")
                if isinstance(val, str):
                    prod["VALUE"] = self._parse_number(val)
                elif isinstance(val, (int, float)):
                    prod["VALUE"] = float(val)
                else:
                    prod["VALUE"] = 0.0

                # UNIT_PRICE (optional)
                up = prod.get("UNIT_PRICE")
                if up is not None:
                    prod["UNIT_PRICE"] = self._parse_number(up) if isinstance(up, str) else float(up)

        except (ValueError, TypeError) as e:
            print(f"⚠️ Lỗi validate types: {e}")
        return data

    def _parse_number(self, value: Union[str, float, int]) -> float:
        """Parse số từ string có dấu phẩy/chấm kiểu Việt Nam"""
        if isinstance(value, (int, float)):
            return float(value)
        if not isinstance(value, str):
            return 0.0

        cleaned = re.sub(r"[^\d.,]", "", value)
        if not cleaned:
            return 0.0

        # VN format: 636.000 hoặc 636,000 = 636000
        if '.' in cleaned and ',' not in cleaned:
            parts = cleaned.split('.')
            if all(len(p) == 3 for p in parts[1:]):
                cleaned = cleaned.replace('.', '')
        elif ',' in cleaned and '.' not in cleaned:
            parts = cleaned.split(',')
            if all(len(p) == 3 for p in parts[1:]):
                cleaned = cleaned.replace(',', '')
            else:
                cleaned = cleaned.replace(',', '.')
        elif ',' in cleaned and '.' in cleaned:
            # 1.234,56 → 1234.56
            cleaned = cleaned.replace('.', '').replace(',', '.')

        try:
            return float(cleaned)
        except ValueError:
            return 0.0

    def validate_total(self, products: List[Dict], total: float) -> bool:
        """Kiểm tra tổng tiền có khớp không"""
        calculated = sum(p.get("NUM", 0) * p.get("UNIT_PRICE", p.get("VALUE", 0)) for p in products)
        # Nếu không có UNIT_PRICE thì dùng VALUE trực tiếp
        if calculated == 0:
            calculated = sum(p.get("VALUE", 0) for p in products)
        tolerance = max(1000, total * 0.02)  # 2% tolerance
        is_valid = abs(calculated - total) <= tolerance
        if not is_valid:
            print(f"⚠️ Tổng tiền không khớp: tính={calculated:,.0f}, hóa đơn={total:,.0f}")
        return is_valid


class DataNormalizer:

    def parse_timestamp(self, timestamp: str) -> str:
        """Chuẩn hóa timestamp về định dạng DD/MM/YYYY HH:MM"""
        if not isinstance(timestamp, str):
            return str(timestamp)

        formats = [
            "%d/%m/%Y %H:%M",
            "%d-%m-%Y %H:%M",
            "%d/%m/%y %H:%M",
            "%d/%m/%Y",
            "%d-%m-%Y",
        ]
        for fmt in formats:
            try:
                dt = datetime.strptime(timestamp.strip(), fmt)
                return dt.strftime("%d/%m/%Y %H:%M")
            except ValueError:
                continue
        return timestamp  # trả nguyên nếu không parse được

    def normalize_currency(self, value) -> float:
        """Chuẩn hóa giá trị tiền tệ"""
        return DataValidator()._parse_number(value)