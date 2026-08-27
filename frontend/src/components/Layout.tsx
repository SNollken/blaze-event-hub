import { useState } from 'react';
import { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { ToastContainer } from './Toast';

interface LayoutProps {
  title: string;
  children: ReactNode;
  headerActions?: ReactNode;
}

export function Layout({ title, children, headerActions }: LayoutProps) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="app-layout">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      {sidebarOpen && (
        <div
          className="sidebar-overlay"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}
      <div className="main-content">
        <Header
          title={title}
          actions={headerActions}
          onMenuClick={() => setSidebarOpen(true)}
        />
        <main className="page-content">{children}</main>
      </div>
      <ToastContainer />
    </div>
  );
}
