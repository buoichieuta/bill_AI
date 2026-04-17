import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Search, Eye, Edit2, Trash2, ArrowLeft, Download, Tag } from 'lucide-react';
import './Pages.css';

interface UserProfile {
  id: string;
  email: string;
  full_name: string;
  avatar_url: string;
  password?: string;
  status: 'active' | 'locked';
}

interface Invoice {
  id: number;
  seller: string;
  total_cost: number;
  timestamp: string;
  user_email: string;
  category: string;
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '---';
  try {
    // Nếu là định dạng DD/MM/YYYY HH:mm
    if (dateStr.includes('/')) {
      const [datePart, timePart] = dateStr.split(' ');
      const [day, month, year] = datePart.split('/');
      // Chuyển sang YYYY-MM-DD để trình duyệt hiểu
      const isoStr = `${year}-${month}-${day}${timePart ? 'T' + timePart : ''}`;
      const date = new Date(isoStr);
      if (!isNaN(date.getTime())) {
        return date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
      }
    }
    // Fallback cho các định dạng khác
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? dateStr : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
  } catch (e) {
    return dateStr;
  }
};

const Users: React.FC = () => {
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserProfile | null>(null);
  const [userInvoices, setUserInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    setLoading(true);
    const { data, error } = await supabase.rpc('get_users_list');
    if (!error && data) {
      setUsers(data.map((u: any) => ({
        ...u,
        status: u.is_locked ? 'locked' : 'active',
        password: u.password || '---'
      })));
    }
    setLoading(false);
  };

  const handleToggleLock = async (user: UserProfile) => {
    const isCurrentlyLocked = user.status === 'locked';
    const confirmMsg = isCurrentlyLocked 
      ? `Bạn có chắc muốn MỞ KHÓA cho người dùng ${user.email}?` 
      : `Bạn có chắc muốn KHÓA người dùng ${user.email}? Người dùng này sẽ không thể đăng nhập.`;
    
    if (!window.confirm(confirmMsg)) return;

    setLoading(true);
    const { error } = await supabase.rpc('lock_user', { 
      user_id: user.id, 
      is_lock: !isCurrentlyLocked 
    });

    if (error) {
      alert('Lỗi: ' + error.message);
    } else {
      // Cập nhật lại danh sách
      await fetchUsers();
      // Nếu đang ở trang chi tiết, cập nhật lại trạng thái selectedUser
      if (selectedUser?.id === user.id) {
        setSelectedUser({
          ...user,
          status: isCurrentlyLocked ? 'active' : 'locked'
        });
      }
    }
    setLoading(false);
  };

  const handleDeleteUser = async (user: UserProfile) => {
    if (!window.confirm(`HÀNH ĐỘNG CỰC KỲ NGUY HIỂM: Bạn có chắc muốn XÓA vĩnh viễn người dùng ${user.email}? Dữ liệu sẽ không thể khôi phục.`)) {
      return;
    }

    setLoading(true);
    const { error } = await supabase.rpc('delete_user_by_admin', { 
      user_id: user.id 
    });

    if (error) {
      alert('Lỗi xóa: ' + error.message);
    } else {
      alert('Đã xóa người dùng thành công.');
      setSelectedUser(null);
      await fetchUsers();
    }
    setLoading(false);
  };

  const handleViewUser = async (user: UserProfile) => {
    setSelectedUser(user);
    setLoading(true);
    // Lấy hóa đơn của riêng user này (Lọc theo ID thay vì Email)
    const { data } = await supabase
      .from('invoices')
      .select('*')
      .eq('user_id', user.id)
      .order('id', { ascending: false });
    
    if (data) setUserInvoices(data);
    setLoading(false);
  };

  const filteredUsers = users.filter(u => 
    u.full_name?.toLowerCase().includes(searchTerm.toLowerCase()) || 
    u.email?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (selectedUser) {
    return (
      <div className="page-fade-in dashboard-content">
        <div className="detail-header">
           <button className="back-btn" onClick={() => setSelectedUser(null)}>
              <ArrowLeft size={18} />
           </button>
           <h2 style={{fontSize: '1.25rem', fontWeight: 700}}>CHI TIẾT NGƯỜI DÙNG - {selectedUser.full_name}</h2>
        </div>

        <div className="user-detail-container">
           {/* Sidebar Thông tin */}
           <div className="user-sidebar">
              <div className="user-profile-card">
                 <img 
                    src={selectedUser.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${selectedUser.id}`} 
                    alt="Avatar" 
                    className="detail-avatar" 
                    onError={(e) => { (e.target as HTMLImageElement).src = `https://api.dicebear.com/7.x/avataaars/svg?seed=${selectedUser.id}`; }}
                 />
                 <h3 style={{fontSize: '1.1rem', marginBottom: '4px'}}>{selectedUser.full_name}</h3>
                 <p style={{fontSize: '0.85rem', color: '#64748b', marginBottom: '1.5rem'}}>{selectedUser.email}</p>
                 
                 <div className="profile-info-item">
                    <span className="label">Trạng thái:</span>
                    <span className={`badge ${selectedUser.status === 'active' ? 'active' : 'locked'}`} style={{padding: '4px 12px', borderRadius: '100px', fontSize: '0.75rem', fontWeight: 'bold'}}>
                        {selectedUser.status === 'active' ? 'Hoạt động' : 'Bị khóa'}
                    </span>
                 </div>

                 <button 
                    className={`lock-btn ${selectedUser.status === 'locked' ? 'unlock' : ''}`}
                    onClick={() => handleToggleLock(selectedUser)}
                    disabled={loading}
                    style={{ background: selectedUser.status === 'locked' ? '#22c55e' : '#e53e3e' }}
                 >
                    {loading ? 'Đang xử lý...' : (selectedUser.status === 'locked' ? 'Mở khóa tài khoản' : 'Khóa tài khoản')}
                 </button>
                 
                 <button 
                    className="lock-btn delete"
                    onClick={() => handleDeleteUser(selectedUser)}
                    style={{ marginTop: '0.5rem', background: '#4a5568' }}
                    disabled={loading}
                 >
                    Xóa người dùng vĩnh viễn
                 </button>
              </div>

              <div className="stats-card-mini">
                 <span className="label">Tổng hóa đơn đã quét:</span>
                 <span className="value">{userInvoices.length}</span>
              </div>
           </div>

           {/* Main Lịch sử quét */}
           <div className="detail-main">
              <div className="detail-main-header">
                 <h3 style={{fontSize: '1rem', fontWeight: 700}}>Lịch sử quét hóa đơn</h3>
                 <div className="search-box miniature">
                    <Search size={14} />
                    <input type="text" placeholder="Search invoice ID/merchant" style={{fontSize: '0.8rem'}} />
                 </div>
              </div>

              <table className="ocr-table dense">
                 <thead>
                    <tr>
                       <th>Mã hóa đơn</th>
                       <th>Thời gian</th>
                       <th>Tổng tiền</th>
                       <th>Nhà cung cấp</th>
                       <th>[Actions]</th>
                    </tr>
                 </thead>
                 <tbody>
                    {userInvoices.map((inv) => (
                       <tr key={inv.id}>
                          <td>HD_{inv.id}</td>
                          <td>{formatDate(inv.timestamp)}</td>
                          <td style={{fontWeight: 700}}>{new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(inv.total_cost)}</td>
                          <td>
                             <div style={{display: 'flex', alignItems: 'center', gap: '6px'}}>
                                <Tag size={12} color="#64748b" />
                                {inv.seller}
                             </div>
                          </td>
                          <td>
                             <div className="action-btns">
                                <button className="add-btn"><Eye size={14} /></button>
                                <button className="add-btn"><Download size={14} /></button>
                             </div>
                          </td>
                       </tr>
                    ))}
                 </tbody>
              </table>
           </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page-fade-in dashboard-content">
      <div className="dashboard-title-bar">
         <h2>QUẢN LÝ NGƯỜI DÙNG</h2>
      </div>

      <div className="action-bar glass" style={{background: 'white', marginBottom: '1.5rem', padding: '1rem', borderRadius: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
        <div style={{display: 'flex', gap: '1rem', alignItems: 'center'}}>
            <span style={{fontSize: '0.85rem', fontWeight: 600, color: '#64748b'}}>Tìm kiếm:</span>
            <div className="search-mini" style={{width: '240px', background: '#f8fafc', border: '1px solid #e2e8f0'}}>
                <Search size={16} color="#94a3b8" />
                <input 
                    type="text" 
                    placeholder="Email hoặc Tên" 
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    style={{background: 'transparent', border: 'none', padding: '6px', fontSize: '0.85rem'}}
                />
            </div>
        </div>
        
        <div style={{display: 'flex', gap: '1rem', alignItems: 'center'}}>
            <span style={{fontSize: '0.85rem', fontWeight: 600, color: '#64748b'}}>Trạng thái:</span>
            <select className="filter-select" style={{padding: '8px 12px', borderRadius: '6px', border: '1px solid #e2e8f0', fontSize: '0.85rem', background: 'white'}}>
                <option>Tất cả</option>
                <option>Hoạt động</option>
                <option>Bị khóa</option>
            </select>
        </div>
      </div>

      <div className="data-section glass">
        <table className="ocr-table">
          <thead>
            <tr>
              <th><input type="checkbox" /></th>
              <th>Tên người dùng</th>
              <th>Email address</th>
              <th>Mật khẩu</th>
              <th>Trạng thái</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
                <tr><td colSpan={6} style={{textAlign: 'center', padding: '2rem'}}>Đang tải dữ liệu...</td></tr>
            ) : filteredUsers.map((user) => (
              <tr key={user.id}>
                <td><input type="checkbox" /></td>
                <td>
                  <div className="user-cell">
                    <img 
                        src={user.avatar_url || `https://api.dicebear.com/7.x/avataaars/svg?seed=${user.id}`} 
                        alt="Avatar" 
                        className="user-avatar" 
                        onError={(e) => { (e.target as HTMLImageElement).src = `https://api.dicebear.com/7.x/avataaars/svg?seed=${user.id}`; }}
                    />
                    <span style={{fontWeight: 600}}>{user.full_name}</span>
                  </div>
                </td>
                <td className="email-cell">{user.email}</td>
                <td style={{ fontWeight: 600, color: '#4a5568' }}>{user.password}</td>
                <td>
                  <span className={`badge ${user.status === 'active' ? 'active' : 'locked'}`} style={{padding: '4px 12px', borderRadius: '100px', fontSize: '0.75rem', fontWeight: 'bold'}}>
                    {user.status === 'active' ? 'Hoạt động' : 'Bị khóa'}
                  </span>
                </td>
                 <td>
                  <div className="action-btns">
                     <button className="add-btn" title="Xem chi tiết" onClick={() => handleViewUser(user)}><Eye size={16} /></button>
                     <button className="add-btn" title="Khóa/Mở khóa" onClick={() => handleToggleLock(user)} style={{color: user.status === 'locked' ? '#22c55e' : ''}}>
                        <Edit2 size={16} />
                     </button>
                     <button className="del-btn" title="Xóa người dùng" onClick={() => handleDeleteUser(user)}><Trash2 size={16} /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default Users;
