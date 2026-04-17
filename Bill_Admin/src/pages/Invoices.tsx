import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { Search, Download, Eye, Tag } from 'lucide-react';
import './Pages.css';

interface Invoice {
  id: number;
  seller: string;
  total_cost: number;
  timestamp: string;
  user_email: string;
  category: string;
}

// Hàm format ngày tháng chung
const formatDate = (dateStr: string) => {
  if (!dateStr) return '---';
  try {
    if (dateStr.includes('/')) {
      const [datePart, timePart] = dateStr.split(' ');
      const [day, month, year] = datePart.split('/');
      const isoStr = `${year}-${month}-${day}${timePart ? 'T' + timePart : ''}`;
      const date = new Date(isoStr);
      if (!isNaN(date.getTime())) {
        return date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
      }
    }
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? dateStr : date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
  } catch (e) {
    return dateStr;
  }
};

const Invoices: React.FC = () => {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    const fetchInvoices = async () => {
      const { data } = await supabase
        .from('invoices')
        .select('*')
        .order('id', { ascending: false });

      if (data) setInvoices(data);
      setLoading(false);
    };

    fetchInvoices();
  }, []);

  const filteredInvoices = invoices.filter(inv => 
    inv.seller?.toLowerCase().includes(searchTerm.toLowerCase()) || 
    inv.id.toString().includes(searchTerm)
  );

  return (
    <div className="page-fade-in dashboard-content">
      <header className="page-header">
        <h1>Quản lý hóa đơn</h1>
        <p>Danh sách các hóa đơn đã được hệ thống xử lý.</p>
      </header>

      <div className="action-bar glass" style={{background: 'white', marginBottom: '1rem', padding: '1rem', borderRadius: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
        <div className="search-box">
          <Search size={18} />
          <input 
            type="text" 
            placeholder="Tìm kiếm theo cửa hàng hoặc mã..." 
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <button className="secondary" style={{display: 'flex', alignItems: 'center', gap: '8.px'}}><Download size={18} /> Xuất CSV</button>
      </div>

      <div className="data-section glass">
        {loading ? <div className="loading-state">Đang tải dữ liệu...</div> : (
          <table className="ocr-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Cửa hàng</th>
                <th>Người dùng</th>
                <th>Tổng tiền</th>
                <th>Danh mục</th>
                <th>Thời gian</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredInvoices.map((inv) => (
                <tr key={inv.id}>
                  <td>#{inv.id}</td>
                  <td className="name" style={{fontWeight: 'bold'}}>{inv.seller}</td>
                  <td className="email-cell">{inv.user_email}</td>
                  <td className="price" style={{color: '#22c55e', fontWeight: 'bold'}}>{new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(inv.total_cost)}</td>
                  <td>
                    <span className="category-tag" style={{display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#64748b', background: '#f1f5f9', padding: '2px 8px', borderRadius: '4px'}}>
                      <Tag size={12} /> {inv.category}
                    </span>
                  </td>
                  <td>{formatDate(inv.timestamp)}</td>
                  <td>
                    <button className="icon-btn"><Eye size={18} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default Invoices;
