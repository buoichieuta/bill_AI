import React from 'react';
import { Search, Bell, ChevronDown } from 'lucide-react';

const Topbar: React.FC = () => {
    const adminSession = JSON.parse(localStorage.getItem('admin_session') || '{}');

    return (
        <header className="topbar">
            <div className="topbar-left">
                <h1>HỆ THỐNG QUẢN LÝ NGƯỜI DÙNG OCR</h1>
            </div>
            
            <div className="topbar-right">
                <div className="search-mini">
                    <Search size={16} />
                    <input type="text" placeholder="Tìm kiếm..." />
                </div>
                
                <div className="notification-icon">
                    <Bell size={20} color="#718096" />
                </div>
                
                <div className="user-profile-top">
                    <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=Admin" alt="Admin" />
                    <span>{adminSession.name || 'Văn A'}</span>
                    <ChevronDown size={14} color="#718096" />
                </div>
            </div>
        </header>
    );
};

export default Topbar;
