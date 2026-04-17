-- CẬP NHẬT HÀM QUẢN LÝ NGƯỜI DÙNG (THÊM CỘT MẬT KHẨU) - CHẠY TRÊN SUPABASE SQL EDITOR

-- 1. Xóa hàm cũ trước
DROP FUNCTION IF EXISTS get_users_list();

-- 2. Cập nhật hàm lấy danh sách User (Thêm cột password lấy từ metadata)
CREATE OR REPLACE FUNCTION get_users_list()
RETURNS TABLE (
    id UUID,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    password TEXT, -- Cột mật khẩu mới
    is_locked BOOLEAN
) 
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        u.id, 
        u.email::TEXT, 
        (u.raw_user_meta_data->>'full_name')::TEXT,
        (u.raw_user_meta_data->>'avatar_url')::TEXT,
        (u.raw_user_meta_data->>'password')::TEXT, -- Lấy mật khẩu từ metadata
        (u.banned_until IS NOT NULL AND u.banned_until > now())
    FROM auth.users u;
END;
$$ LANGUAGE plpgsql;

-- Cấp lại quyền
GRANT EXECUTE ON FUNCTION get_users_list() TO anon, authenticated, service_role;
