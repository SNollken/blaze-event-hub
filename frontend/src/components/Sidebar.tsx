import { NavLink } from 'react-router-dom';
import { useState, useEffect, useRef } from 'react';
import { useI18n } from '../i18n/I18nContext';
import { getOAuthSession, getMe } from '../api/client';
import type { OAuthSessionResponse, MemberProfile } from '../api/client';

const navItems = [
  { to: '/', labelKey: 'navDashboard' as const, icon: 'dashboard' },
  { to: '/events/create', labelKey: 'navCreate' as const, icon: 'plus' },
  { to: '/events', labelKey: 'navAllEvents' as const, icon: 'calendar' },
  { to: '/my-events', labelKey: 'navMyEvents' as const, icon: 'users' },
];

const icons: Record<string, JSX.Element> = {
  dashboard: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
      <rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
    </svg>
  ),
  plus: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>
    </svg>
  ),
  calendar: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <rect x="3" y="4" width="18" height="18" rx="2"/><path d="M3 10h18M8 2v4M16 2v4"/>
    </svg>
  ),
  users: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/>
      <path d="M22 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
    </svg>
  ),
};

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const { t } = useI18n();
  const [oauth, setOAuth] = useState<OAuthSessionResponse | null>(null);
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let alive = true;
    getOAuthSession()
      .then(async (s) => {
        if (!alive) return;
        setOAuth(s);
        if (s.connected && s.profilePresent) {
          try {
            const m = await getMe();
            if (alive) setMember(m);
          } catch { /* ignore */ }
        }
      })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  // Close dropdown on outside click
  useEffect(() => {
    if (!menuOpen) return;
    function handleClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [menuOpen]);

  const connected = oauth?.connected === true;
  const name = member?.displayName || oauth?.profile?.displayName || oauth?.profile?.username || '';
  const avatarUrl = member?.avatarUrl || oauth?.profile?.avatarUrl || null;

  const handleLogout = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setMenuOpen(false);
    try {
      await fetch('/api/blaze/oauth/logout', { method: 'POST' });
    } catch { /* ignore */ }
    window.location.href = '/login';
  };

  const handleNavClick = () => {
    onClose();
  };

  return (
    <aside id="sidebar" className={open ? 'open' : undefined}>
      {/* Brand */}
      <div className="sb-brand">
        <NavLink to="/" className="sb-logo" onClick={handleNavClick}>
          <span className="logo-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
              <rect x="3" y="4" width="18" height="18" rx="2"/>
              <path d="M3 10h18M8 2v4M16 2v4"/>
              <circle cx="12" cy="16" r="2" fill="currentColor" stroke="none"/>
            </svg>
          </span>
          {t('dashTitle')}
        </NavLink>
        <div className="sb-sub">{t('brandSub')}</div>
      </div>

      {/* Navigation */}
      <nav className="sb-nav">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            onClick={handleNavClick}
            className={({ isActive }) => isActive ? 'active' : ''}
          >
            <span style={{ opacity: 0.5, display: 'flex' }}>{icons[item.icon]}</span>
            {t(item.labelKey)}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div className="sb-footer">
        {connected && name ? (
          <div ref={menuRef} style={{ position: 'relative', width: '100%' }}>
            <button className="sb-profile" onClick={() => setMenuOpen(!menuOpen)}>
              <div className="sb-pfp">
                {avatarUrl ? (
                  <img src={avatarUrl} alt="" style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} />
                ) : (
                  name[0]?.toUpperCase()
                )}
              </div>
              <div className="sb-profile-info">
                <span className="sb-username">{name}</span>
                <span className="sb-status">{t('sbConnected')}</span>
              </div>
            </button>

            <div className={`sb-profile-menu${menuOpen ? ' show' : ''}`}>
              <button onClick={handleLogout}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                  <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/>
                  <polyline points="16 17 21 12 16 7"/>
                  <line x1="21" y1="12" x2="9" y2="12"/>
                </svg>
                {t('sbLogout')}
              </button>
            </div>
          </div>
        ) : (
          <span className="sb-version">v1.0</span>
        )}
      </div>
    </aside>
  );
}
