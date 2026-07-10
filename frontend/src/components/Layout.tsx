import { type ReactNode, useState } from 'react';
import { Sidebar } from './Sidebar';
import { useI18n } from '../i18n/I18nContext';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  const { t } = useI18n();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const closeSidebar = () => setSidebarOpen(false);

  return (
    <div className="app-layout">
      {/* Mobile hamburger button */}
      <button
        className="mobile-menu-btn"
        onClick={() => setSidebarOpen(true)}
        aria-label={t('menuLabel')}
      >
        ☰
      </button>

      {/* Sidebar overlay — closes sidebar on click */}
      <div
        id="sidebar-overlay"
        className={sidebarOpen ? 'show' : undefined}
        onClick={closeSidebar}
      />

      {/* Sidebar */}
      <Sidebar open={sidebarOpen} onClose={closeSidebar} />

      {/* Main content area */}
      <div className="main-content">
        <div className="page-content">
          {children}
        </div>
      </div>
    </div>
  );
}
