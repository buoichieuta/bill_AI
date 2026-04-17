-- Xóa hàm cũ trước để thay đổi cấu trúc trả về
DROP FUNCTION IF EXISTS get_users_list();

-- Tạo lại hàm mới với đầy đủ thông tin Avatar
CREATE OR REPLACE FUNCTION get_users_list()
RETURNS TABLE (
    id UUID,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT 
) 
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        u.id, 
        u.email::TEXT, 
        (u.raw_user_meta_data->>'full_name')::TEXT,
        (u.raw_user_meta_data->>'avatar_url')::TEXT 
    FROM auth.users u;
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION get_users_list() TO anon, authenticated, service_role;
