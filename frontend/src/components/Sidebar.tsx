import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { disconnectOAuth, getMe, getOAuthSession } from '../api/client';
import type { MemberProfile, OAuthSessionResponse } from '../api/client';
import { AUTH_SESSION_CHANGED_EVENT, notifyAuthSessionChanged } from '../auth-session';
import { useI18n } from '../i18n/I18nContext';

const navItems = [
  { to: '/', labelKey: 'navDashboard' as const, icon: 'explore' },
  { to: '/events/create', labelKey: 'navCreate' as const, icon: 'plus' },
  { to: '/my-events', labelKey: 'navMyEvents' as const, icon: 'events' },
  { to: '/settings/blaze', labelKey: 'navStudioChannel' as const, icon: 'link' },
  { to: '/help', labelKey: 'navHelp' as const, icon: 'help' },
];

const icons: Record<string, JSX.Element> = {
  explore: <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" /><path d="m15.5 8.5-2.2 4.8-4.8 2.2 2.2-4.8 4.8-2.2Z" /></svg>,
  plus: <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" /><path d="M12 8v8M8 12h8" /></svg>,
  events: <svg viewBox="0 0 24 24"><path d="M5 5h14v14H5zM8 3v4M16 3v4M5 9h14" /></svg>,
  link: <svg viewBox="0 0 24 24"><path d="M9.5 14.5 14.5 9M7.5 16.5l-1 1a3.5 3.5 0 0 1-5-5l3-3a3.5 3.5 0 0 1 5 0M16.5 7.5l1-1a3.5 3.5 0 0 1 5 5l-3 3a3.5 3.5 0 0 1-5 0" /></svg>,
  help: <svg viewBox="0 0 24 24"><path d="M5 4h10a4 4 0 0 1 4 4v12l-4-3H5z" /><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .8-1 1.4M12 15h.01" /></svg>,
};

interface SidebarProps {
  open: boolean;
  mobile?: boolean;
  onClose: () => void;
}

export function Sidebar({ open, mobile = false, onClose }: SidebarProps) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [oauth, setOAuth] = useState<OAuthSessionResponse | null>(null);
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const sidebarRef = useRef<HTMLElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const profileButtonRef = useRef<HTMLButtonElement>(null);

  useLayoutEffect(() => {
    const sidebar = sidebarRef.current;
    if (!sidebar) return;
    if (mobile && !open) sidebar.setAttribute('inert', '');
    else sidebar.removeAttribute('inert');
  }, [mobile, open]);

  useEffect(() => {
    if (mobile && !open) setMenuOpen(false);
  }, [mobile, open]);

  useEffect(() => {
    let active = true;
    const loadAccount = () => getOAuthSession()
      .then(async (session) => {
        if (!active) return;
        setOAuth(session);
        if (!session.connected) {
          setMember(null);
          return;
        }
        try {
          const profile = await getMe();
          if (active) setMember(profile);
        } catch {
          // O resumo OAuth ainda permite renderizar a conta conectada.
        }
      })
      .catch(() => undefined);
    const handleSessionChanged = () => void loadAccount();

    void loadAccount();
    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, handleSessionChanged);
    return () => {
      active = false;
      window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, handleSessionChanged);
    };
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const handlePointer = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const handleKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      setMenuOpen(false);
      window.requestAnimationFrame(() => profileButtonRef.current?.focus());
    };
    document.addEventListener('mousedown', handlePointer);
    window.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      window.removeEventListener('keydown', handleKey);
    };
  }, [menuOpen]);

  const connected = oauth?.connected === true;
  const name = member?.displayName
    || oauth?.profile?.displayName
    || oauth?.profile?.username
    || t('sidebarCreatorFallback');
  const avatarUrl = member?.avatarUrl || oauth?.profile?.avatarUrl || null;

  const handleLogout = async () => {
    setMenuOpen(false);
    setLogoutError('');
    try {
      await disconnectOAuth();
      setOAuth(null);
      setMember(null);
      notifyAuthSessionChanged();
      navigate('/login', { replace: true });
    } catch {
      setLogoutError(t('logoutError'));
    }
  };

  return (
    <aside
      ref={sidebarRef}
      id="sidebar"
      className={open ? 'open' : undefined}
      aria-label={t('sidebarPrimaryNavigation')}
      aria-hidden={mobile && !open ? true : undefined}
    >
      <div className="sb-brand">
        <NavLink to="/" className="sb-logo" onClick={onClose}>
          <span className="logo-icon" aria-hidden="true">
            <svg data-app-mark="giveaway" viewBox="0 0 24 24">
              <path d="M4 10h16v10H4zM3 7h18v4H3zM12 7v13" />
              <path d="M12 7H9.6C7.9 7 7 6.1 7 4.8 7 3.8 7.8 3 8.9 3 10.7 3 12 5.1 12 7Zm0 0h2.4C16.1 7 17 6.1 17 4.8 17 3.8 16.2 3 15.1 3 13.3 3 12 5.1 12 7Z" />
            </svg>
          </span>
          <span>Blaze Event Hub</span>
        </NavLink>
        <p className="sb-sub">{t('sidebarSubtitle')}</p>
        <button
          type="button"
          className="sidebar-close"
          onClick={onClose}
          aria-label={t('sidebarCloseNavigation')}
        >
          {t('close')}
        </button>
      </div>

      <nav className="sb-nav" aria-label={t('sidebarProductSections')}>
        <span className="sb-nav-label">{t('sidebarControlCenter')}</span>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            onClick={onClose}
            className={({ isActive }) => isActive ? 'active' : ''}
          >
            <span className="sb-nav-icon" aria-hidden="true">{icons[item.icon]}</span>
            {t(item.labelKey)}
          </NavLink>
        ))}
      </nav>

      <div className="sb-footer">
        {connected ? (
          <div ref={menuRef} className="sb-account">
            <button
              ref={profileButtonRef}
              type="button"
              className="sb-profile"
              onClick={() => setMenuOpen((current) => !current)}
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              aria-controls="sidebar-profile-menu"
            >
              <span className="sb-pfp">
                {avatarUrl ? <img src={avatarUrl} alt="" /> : name[0]?.toUpperCase()}
              </span>
              <span className="sb-profile-info">
                <span className="sb-username">{name}</span>
                <span className="sb-status">{t('sidebarConnected')}</span>
              </span>
            </button>
            <div
              id="sidebar-profile-menu"
              className={`sb-profile-menu${menuOpen ? ' show' : ''}`}
              role="menu"
              hidden={!menuOpen}
            >
              <button type="button" role="menuitem" onClick={() => void handleLogout()}>{t('sbLogout')}</button>
            </div>
            {logoutError && <p className="sb-error" role="alert">{logoutError}</p>}
          </div>
        ) : (
          <NavLink className="sb-connect" to="/login" onClick={onClose}>{t('sidebarConnectBlaze')}</NavLink>
        )}
      </div>
    </aside>
  );
}
