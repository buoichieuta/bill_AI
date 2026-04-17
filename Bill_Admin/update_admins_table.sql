-- Cập nhật bảng admins để hỗ trợ trang Cài đặt

-- 1. Thêm cột avatar_url nếu chưa có
ALTER TABLE public.admins ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- 2. Cập nhật RLS để cho phép lấy danh sách Admin hiển thị trong trang Cài đặt
-- (Lưu ý: Chỉ danh sách công khai, mật khẩu vẫn được bảo vệ)
DROP POLICY IF EXISTS "Allow select for all" ON public.admins;
CREATE POLICY "Allow select for all" ON public.admins FOR SELECT USING (true);

-- 3. Tạo một số dữ liệu demo nếu bạn muốn (Tùy chọn)
-- UPDATE public.admins SET avatar_url = 'https://api.dicebear.com/7.x/avataaars/svg?seed=Admin1' WHERE email = 'chieu@gmail.com';
