-- CHẠY LỆNH NÀY TRONG SUPABASE SQL EDITOR ĐỂ SỬA LỖI KHÔNG HIỆN BILL
-- Lệnh này cho phép Web Admin được quyền đọc dữ liệu từ bảng invoices

-- 1. Cho phép đọc bảng invoices
DROP POLICY IF EXISTS "Enable read access for all users" ON public.invoices;
CREATE POLICY "Enable read access for all users" ON public.invoices FOR SELECT USING (true);

-- 2. Đảm bảo bảng invoices được bật bảo mật RLS
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;

-- 3. (Tùy chọn) Cho phép đọc bảng products (nếu bạn muốn xem chi tiết món hàng trong đơn)
DROP POLICY IF EXISTS "Enable read access for products" ON public.products;
CREATE POLICY "Enable read access for products" ON public.products FOR SELECT USING (true);
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
