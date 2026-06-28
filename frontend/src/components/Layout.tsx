import { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { ToastContainer } from './Toast';

interface LayoutProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  headerActions?: ReactNode;
}

export function Layout({ title, subtitle, children, headerActions }: LayoutProps) {
  return (
    <div className="app-layout">
      <Sidebar />
      <div className="main-content">
        <Header title={title} subtitle={subtitle} actions={headerActions} />
        <main className="page-content">{children}</main>
      </div>
      <ToastContainer />
    </div>
  );
}
