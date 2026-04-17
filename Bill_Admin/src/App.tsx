import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Topbar from './components/Topbar';
import Dashboard from './pages/Dashboard';
import Users from './pages/Users';
import Invoices from './pages/Invoices';
import Feedback from './pages/Feedback';
import Settings from './pages/Settings';
import Login from './pages/Login';

const App: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);

  useEffect(() => {
    const session = localStorage.getItem('admin_session');
    setIsAuthenticated(!!session);
  }, []);

  if (isAuthenticated === null) return null;

  if (!isAuthenticated) {
    return (
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="*" element={<Navigate to="/login" />} />
        </Routes>
      </Router>
    );
  }

  return (
    <Router>
      <div className="app-container">
        <Sidebar />
        <div className="main-content">
          <Topbar />
          <div className="page-wrapper">
             <Routes>
               <Route path="/" element={<Dashboard />} />
               <Route path="/users" element={<Users />} />
               <Route path="/invoices" element={<Invoices />} />
               <Route path="/feedback" element={<Feedback />} />
               <Route path="/settings" element={<Settings />} />
               <Route path="*" element={<Navigate to="/" />} />
             </Routes>
          </div>
        </div>
      </div>
    </Router>
  );
};

export default App;
