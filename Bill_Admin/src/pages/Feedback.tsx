import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { MessageSquare, CheckCircle, Clock } from 'lucide-react';
import './Pages.css';

interface FeedbackItem {
  id: number;
  user_email: string;
  category: string;
  description: string;
  created_at: string;
}

const Feedback: React.FC = () => {
  const [feedbacks, setFeedbacks] = useState<FeedbackItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchFeedbacks = async () => {
      const { data } = await supabase
        .from('feedbacks')
        .select('*')
        .order('id', { ascending: false });

      if (data) setFeedbacks(data);
      setLoading(false);
    };

    fetchFeedbacks();
  }, []);

  return (
    <div className="page-fade-in dashboard-content">
      <header className="page-header">
        <h1>Phản hồi & Hỗ trợ</h1>
        <p>Lắng nghe ý kiến từ người dùng để cải thiện ứng dụng.</p>
      </header>

      <div className="feedback-list">
        {loading ? <div className="loading-state">Đang tải...</div> : feedbacks.map((item) => (
          <div key={item.id} className="feedback-card glass" style={{background: 'white', padding: '1.5rem', borderRadius: '12.px', marginBottom: '1.5rem'}}>
            <div className="feedback-header" style={{display: 'flex', justifyContent: 'space-between', marginBottom: '1rem'}}>
              <div className="user-info" style={{display: 'flex', alignItems: 'center', gap: '1rem'}}>
                 <div className="avatar" style={{width: '36px', height: '36px', borderRadius: '50%', background: '#0ea5e9', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold'}}>{item.user_email.charAt(0).toUpperCase()}</div>
                 <div>
                    <div className="email" style={{fontWeight: 'bold'}}>{item.user_email}</div>
                    <div className="date" style={{fontSize: '0.75rem', color: '#64748b'}}>{new Date(item.created_at).toLocaleString('vi-VN')}</div>
                 </div>
              </div>
              <span className="badge active" style={{background: '#e0f2fe', color: '#0ea5e9', padding: '4px 12px', borderRadius: '100px', fontSize: '0.75rem', fontWeight: 'bold'}}>{item.category}</span>
            </div>
            <div className="feedback-content" style={{marginBottom: '1.5rem'}}>
              <p>{item.description}</p>
            </div>
            <div className="feedback-footer" style={{display: 'flex', gap: '1rem', justifyContent: 'flex-end'}}>
              <button className="secondary" style={{display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 16px', background: '#f1f5f9', border: 'none', borderRadius: '6px', fontSize: '0.85rem'}}><Clock size={16} /> Đang xử lý</button>
              <button className="primary" style={{display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 16px', background: '#0ea5e9', color: 'white', border: 'none', borderRadius: '6px', fontSize: '0.85rem'}}><CheckCircle size={16} /> Hoàn tất</button>
            </div>
          </div>
        ))}
        {!loading && feedbacks.length === 0 && (
          <div className="empty-state glass" style={{textAlign: 'center', padding: '4rem', color: '#64748b'}}>
             <MessageSquare size={48} />
             <p>Hiện chưa có phản hồi nào từ người dùng.</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Feedback;
