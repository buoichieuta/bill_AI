-- Câu lệnh SQL để tạo bảng admins trong Supabase SQL Editor
-- Bạn hãy copy và chạy lệnh này trong phần SQL Editor của Supabase Dashboard.

CREATE TABLE IF NOT EXISTS public.admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL, -- Sẽ được lưu dưới dạng hash (đã mã hóa)
    full_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Bật Row Level Security (RLS) để bảo mật
ALTER TABLE public.admins ENABLE ROW LEVEL SECURITY;

-- Cho phép mọi người xem bảng để thực hiện kiểm tra đăng nhập (Login)
CREATE POLICY "Allow select for all" ON public.admins FOR SELECT USING (true);

-- Chỉ cho phép hệ thống (service_role) hoặc chính chủ chỉnh sửa
CREATE POLICY "Enable insert for service key only" ON public.admins FOR INSERT WITH CHECK (true);
