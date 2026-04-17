import React from 'react';
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Users, Receipt, MessageSquare, Settings, LogOut } from 'lucide-react';
import './Sidebar.css';

const Sidebar: React.FC = () => {
  const handleLogout = () => {
    localStorage.removeItem('admin_session');
    window.location.href = '/login';
  };

  return (
    <div className="sidebar">
      <div className="sidebar-header">
        <div className="logo">
          OCR <span>ADMIN</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        <NavLink to="/" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <LayoutDashboard size={20} />
          <span>Tổng quan</span>
        </NavLink>
        <NavLink to="/users" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <Users size={20} />
          <span>Người dùng</span>
        </NavLink>
        <NavLink to="/invoices" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <Receipt size={20} />
          <span>Hóa đơn</span>
        </NavLink>
        <NavLink to="/feedback" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <MessageSquare size={20} />
          <span>Phản hồi</span>
        </NavLink>
        <NavLink to="/settings" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <Settings size={20} />
          <span>Cài đặt</span>
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <button className="logout-btn" onClick={handleLogout}>
          <LogOut size={20} />
          <span>Đăng xuất</span>
        </button>
      </div>
    </div>
  );
};

export default Sidebar;
