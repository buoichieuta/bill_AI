import bcrypt
from supabase import create_client, Client
import getpass

# Cấu hình Supabase
SUPABASE_URL = "https://oypyxgzmlwdabcwahobl.supabase.co"
# Gợi ý: Nên dùng Service Role Key để có quyền Insert vào bảng admins nếu có RLS
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im95cHl4Z3ptbHdkYWJjd2Fob2JsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUxMDk1NzEsImV4cCI6MjA5MDY4NTU3MX0.wb3u4DklOjMm8KZ_fO98zsNpoV1LQ8xxI104Uh2H0jk"

supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

def register_admin():
    print("=== Đăng ký tài khoản OCR Admin ===")
    email = input("Nhập Email Admin: ")
    password = getpass.getpass("Nhập Mật khẩu Admin: ")
    full_name = input("Nhập Tên đầy đủ: ")

    # Mã hóa mật khẩu bằng bcrypt
    salt = bcrypt.gensalt()
    hashed_password = bcrypt.hashpw(password.encode('utf-8'), salt).decode('utf-8')

    try:
        data = {
            "email": email,
            "password": hashed_password,
            "full_name": full_name
        }
        
        response = supabase.table("admins").insert(data).execute()
        
        if response.data:
            print(f"\n[Thành công] Đã đăng ký Admin: {email}")
        else:
            print(f"\n[Lỗi] Không thể đăng ký: {response}")
            
    except Exception as e:
        print(f"\n[Lỗi] Đã xảy ra lỗi: {str(e)}")

if __name__ == "__main__":
    register_admin()
