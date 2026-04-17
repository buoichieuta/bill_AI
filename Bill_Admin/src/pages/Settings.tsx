import React from 'react';
import { Camera, LogOut } from 'lucide-react';
import './Pages.css';

interface StaticAdmin {
    name: string;
    role: string;
    mission: string;
    image: string;
    email: string;
}

const STATIC_ADMINS: StaticAdmin[] = [
    {
        name: 'NGUYỄN QUỐC TÍN',
        role: 'ADMIN',
        mission: 'SHOPPE FOOD',
        image: '/assets/admins/admin1.png',
        email: 'q.tin@billai.com'
    },
    {
        name: 'TRỊNH VĂN MẠNH',
        role: 'ADMIN',
        mission: 'XANH SM AN TOÀN',
        image: '/assets/admins/admin2.png',
        email: 'v.manh@billai.com'
    },
    {
        name: 'TRẦN NGUYỄN NHẬT TOÀN',
        role: 'ADMIN',
        mission: 'ANH GRAB MAY MẮN',
        image: '/assets/admins/admin3.png',
        email: 'n.toan@billai.com'
    },
    {
        name: 'NGÔ VĂN CHIỀU',
        role: 'ADMIN',
        mission: 'ANH BE CHĂM CHỈ',
        image: '/assets/admins/admin4.png',
        email: 'v.chieu@billai.com'
    }
];

const Settings: React.FC = () => {
    const handleLogout = () => {
        localStorage.removeItem('admin_session');
        window.location.href = '/login';
    };

    return (
        <div className="page-fade-in dashboard-content">
            <header className="page-header" style={{ marginBottom: '2rem' }}>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 700, textTransform: 'uppercase' }}>CÀI ĐẶT HỒ SƠ & TÀI KHOẢN</h2>
            </header>

            <div className="admins-grid">
                {STATIC_ADMINS.map((admin, index) => (
                    <div key={index} className="admin-card">
                        <div className="admin-avatar-wrapper">
                            <img src={admin.image} alt={admin.name} />
                        </div>
                        <div className="admin-info">
                            <h3>{admin.name}</h3>
                            <p style={{ color: '#0ea5e9', fontWeight: 700, fontSize: '0.8rem', marginBottom: '8px' }}>{admin.role}</p>
                            <p style={{ fontSize: '0.8rem', color: '#64748b', fontStyle: 'italic' }}>{admin.mission}</p>
                            <p style={{ fontSize: '0.75rem', marginTop: '8px', color: '#94a3b8' }}>{admin.email}</p>
                        </div>
                    </div>
                ))}
            </div>

            <div className="settings-footer" style={{ marginTop: '3rem', display: 'flex', justifyContent: 'center' }}>
                <button className="logout-action-btn" onClick={handleLogout}>
                    <LogOut size={20} /> Đăng xuất
                </button>
            </div>
        </div>
    );
};

export default Settings;
