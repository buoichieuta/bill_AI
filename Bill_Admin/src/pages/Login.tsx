import React, { useState } from 'react';
import { supabase } from '../lib/supabase';
import { Mail, Lock, EyeOff, Eye } from 'lucide-react';
import bcrypt from 'bcryptjs';
import './Login.css';

const Login: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      // 1. Tìm admin theo email
      const { data: admins, error: fetchError } = await supabase
        .from('admins')
        .select('*')
        .eq('email', email)
        .single();

      if (fetchError || !admins) {
          setError('Tài khoản hoặc mật khẩu không chính xác.');
          setLoading(false);
          return;
      }

      // 2. Kiểm tra mật khẩu (bcrypt)
      const isMatch = await bcrypt.compare(password, admins.password);
      if (isMatch) {
          // Thành công - Lưu session giả (vì ta dùng bảng tùy chỉnh)
          localStorage.setItem('admin_session', JSON.stringify({
              id: admins.id,
              email: admins.email,
              name: admins.full_name
          }));
          window.location.href = '/';
      } else {
          setError('Tài khoản hoặc mật khẩu không chính xác.');
      }
    } catch (err) {
      setError('Đã xảy ra lỗi hệ thống.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      <div className="login-container">
        <div className="brand-header">
            <div className="brand-logo">
                <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_Y8u3_T_7_Xf_q_B_f_P_y_s_8_x_k_8_s_8_s&s" alt="Logo" className="logo-img" />
                <span className="brand-name">OCR <span>ADMIN</span></span>
            </div>
            <div className="brand-title">HỆ THỐNG QUẢN LÝ NGƯỜI DÙNG OCR</div>
        </div>

        <div className="login-box glass">
          <div className="box-logo">
             <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_Y8u3_T_7_Xf_q_B_f_P_y_s_8_x_k_8_s_8_s&s" alt="Logo" />
          </div>
          <h2>Đăng nhập vào Tài khoản</h2>
          
          <form onSubmit={handleLogin}>
            <div className="input-group">
              <label>Email</label>
              <div className="input-field">
                <Mail size={18} className="icon" />
                <input 
                  type="email" 
                  placeholder="Nhập email của bạn" 
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required 
                />
              </div>
            </div>

            <div className="input-group">
              <label>Mật khẩu</label>
              <div className="input-field">
                <Lock size={18} className="icon" />
                <input 
                  type={showPassword ? "text" : "password"} 
                  placeholder="Nhập mật khẩu" 
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required 
                />
                <button 
                  type="button" 
                  className="toggle-password"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <Eye size={18} /> : <EyeOff size={18} />}
                </button>
              </div>
            </div>

            <div className="form-options">
               <label className="checkbox-container">
                  <input type="checkbox" />
                  <span className="checkmark"></span>
                  Ghi nhớ đăng nhập
               </label>
               <a href="#" className="forgot-link">Quên mật khẩu?</a>
            </div>

            {error && <div className="error-message">{error}</div>}

            <button type="submit" className="login-btn" disabled={loading}>
              {loading ? 'Đang xử lý...' : 'Đăng nhập'}
            </button>

            <div className="divider">
               <span>Hoặc đăng nhập với</span>
            </div>

            <div className="social-login">
               <button type="button" className="social-btn google">
                  <img src="https://www.gstatic.com/images/branding/product/1x/gsa_512dp.png" alt="Google" />
               </button>
               <button type="button" className="social-btn facebook">
                  <img src="https://upload.wikimedia.org/wikipedia/commons/b/b8/2021_Facebook_icon.svg" alt="FB" />
               </button>
            </div>

            <div className="signup-prompt">
              Chưa có tài khoản? <a href="#">Đăng ký ngay</a>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;
