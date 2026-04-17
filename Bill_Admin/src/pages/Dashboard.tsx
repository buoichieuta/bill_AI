import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Users, UserPlus, FileSearch, CheckCircle2 } from 'lucide-react';
import './Pages.css';

interface DashboardStats {
  totalUsers: number;
  newToday: number;
  totalScans: number;
  successRate: number;
}

interface UserProfile {
  name: string;
  email: string;
  avatar: string;
}

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats>({
    totalUsers: 0,
    newToday: 0,
    totalScans: 0,
    successRate: 98.2
  });
  const [recentUsers, setRecentUsers] = useState<UserProfile[]>([]);
  const [recentActivities, setRecentActivities] = useState<any[]>([]);

  useEffect(() => {
    const fetchDashboardData = async () => {
      // 1. Lấy danh sách users từ RPC (Hàm SQL bạn vừa chạy)
      const { data: authUsers } = await supabase.rpc('get_users_list');
      
      // 2. Lấy dữ liệu invoices để đếm lượt quét và làm hoạt động gần đây
      const { data: invoices } = await supabase
        .from('invoices')
        .select('user_email, created_at, seller')
        .order('id', { ascending: false });
      
      if (authUsers) {
        const today = new Date().toISOString().split('T')[0];
        
        // Cập nhật thống kê
        setStats({
          totalUsers: authUsers.length,
          newToday: authUsers.filter((u: any) => u.created_at?.startsWith(today)).length || 0,
          totalScans: invoices?.length || 0,
          successRate: 98.2 // Giá trị DEMO
        });

        // Cập nhật danh sách User
        const usersList: UserProfile[] = authUsers.slice(0, 15).map((u: any) => ({
          name: u.full_name || u.email.split('@')[0],
          email: u.email,
          avatar: u.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${u.id}`
        }));
        setRecentUsers(usersList);

        // Cập nhật Hoạt động gần đây (Lấy 8 cái mới nhất)
        if (invoices) {
            setRecentActivities(invoices.slice(0, 8));
        }
      }
    };

    fetchDashboardData();
  }, []);

  return (
    <div className="dashboard-content page-fade-in ocr-admin-theme">
      <div className="dashboard-title-bar">
         <h2>Bảng điều khiển tổng quan</h2>
      </div>

      <div className="stats-row">
        <div className="stat-box glass">
          <div className="stat-icon-wrapper blue"><Users size={20} /></div>
          <div className="stat-data">
             <div className="stat-label">Tổng số người dùng</div>
             <div className="stat-main">
                <span className="value">{stats.totalUsers.toLocaleString()}</span>
                <span className="trend pos">▲ 12.82%</span>
             </div>
          </div>
        </div>
        <div className="stat-box glass">
          <div className="stat-icon-wrapper light-blue"><UserPlus size={20} /></div>
          <div className="stat-data">
             <div className="stat-label">Người dùng mới hôm nay</div>
             <div className="stat-main">
                <span className="value">{stats.newToday}</span>
             </div>
          </div>
        </div>
        <div className="stat-box glass">
          <div className="stat-icon-wrapper green"><FileSearch size={20} /></div>
          <div className="stat-data">
             <div className="stat-label">Tổng lượt quét OCR</div>
             <div className="stat-main">
                <span className="value">{stats.totalScans.toLocaleString()}</span>
             </div>
          </div>
        </div>
        <div className="stat-box glass">
          <div className="stat-icon-wrapper teal"><CheckCircle2 size={20} /></div>
          <div className="stat-data">
             <div className="stat-label">Tỷ lệ thành công</div>
             <div className="stat-main">
                <span className="value">{stats.successRate}%</span>
             </div>
          </div>
        </div>
      </div>

      <div className="main-grid split">
        {/* User Table (Removed Registration Date Column) */}
        <div className="data-section glass">
           <div className="section-header">
              <h3>Người dùng mới gần đây</h3>
           </div>
           <table className="ocr-table dense">
              <thead>
                 <tr>
                    <th>Người dùng</th>
                    <th>Email</th>
                    <th>[Actions]</th>
                 </tr>
              </thead>
              <tbody>
                 {recentUsers.map((user, i) => (
                    <tr key={i}>
                       <td>
                          <div className="user-cell">
                             <img 
                                src={user.avatar} 
                                alt="Avatar" 
                                className="user-avatar" 
                                onError={(e) => {
                                   (e.target as HTMLImageElement).src = `https://api.dicebear.com/7.x/avataaars/svg?seed=${user.name}`;
                                }}
                             />
                             <span>{user.name}</span>
                          </div>
                       </td>
                       <td className="email-cell">{user.email}</td>
                       <td>
                          <div className="action-btns">
                             <button className="add-btn"><UserPlus size={14} /></button>
                             <button className="del-btn" style={{color: '#d1d5db'}}><AlertTriangle size={14} /></button>
                          </div>
                       </td>
                    </tr>
                 ))}
              </tbody>
           </table>
        </div>

        {/* Activity Sidebar */}
        <div className="activity-section glass">
           <div className="section-header">
              <h3>Hoạt động hệ thống gần đây</h3>
           </div>
           <div className="activity-list">
              {recentActivities.length > 0 ? recentActivities.map((act, i) => (
                 <div key={i} className={`activity-item ${i % 3 === 0 ? 'success' : ''}`}>
                    <div className="activity-title">
                        User <strong>{act.user_email}</strong> vừa nhận diện hóa đơn tại <strong>{act.seller}</strong>
                    </div>
                    <div className="activity-time">
                        {new Date(act.created_at || Date.now()).toLocaleString('vi-VN')}
                    </div>
                 </div>
              )) : (
                 <div className="activity-item">Chưa có hoạt động nào hôm nay</div>
              )}
           </div>
        </div>
      </div>
    </div>
  );
};

// Help icons from lucide
const AlertTriangle = ({ size, style }: any) => <svg style={style} xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-alert-triangle"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>;

export default Dashboard;
