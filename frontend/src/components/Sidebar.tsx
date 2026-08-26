import { useCallback, useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Radio,
  RadioTower,
  Bell,
  Gift,
  Layers,
  Flame,
  KeyRound,
} from 'lucide-react';
import { t } from '../i18n';
import { usePolling } from '../hooks/usePolling';
import { getStatus, startOAuth } from '../api/client';
import { addToast } from './Toast';

const navItems = [
  { to: '/', labelKey: 'nav.dashboard', icon: LayoutDashboard },
  { to: '/events', labelKey: 'nav.events', icon: Radio },
  { to: '/blaze', labelKey: 'nav.blaze', icon: RadioTower },
  { to: '/alerts', labelKey: 'nav.alerts', icon: Bell },
  { to: '/giveaways', labelKey: 'nav.giveaways', icon: Gift },
  { to: '/overlays', labelKey: 'nav.overlays', icon: Layers },
];

function AccountFooter() {
  const fetchStatus = useCallback(() => getStatus(), []);
  const { data: status } = usePolling(fetchStatus, 20000);
  const [connecting, setConnecting] = useState(false);
  const connected = !!status?.oauthConnected;

  const handleConnect = async () => {
    setConnecting(true);
    try {
      const res = await startOAuth();
      window.open(res.authorizationUrl, '_blank', 'noopener,noreferrer');
      addToast('success', t('blaze.connectSuccess'));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('blaze.connectError'));
    } finally {
      setConnecting(false);
    }
  };

  return (
    <div className="px-3 py-3 border-t border-border-default">
      {connected ? (
        <div className="flex items-center gap-2 px-2 min-w-0" title={t('home.blazeAccount')}>
          <span className="status-dot active flex-shrink-0" aria-hidden="true" />
          <span className="text-[12px] text-text-secondary truncate">
            {status?.connectedAccountDisplayName || t('common.connected')}
          </span>
        </div>
      ) : (
        <button className="btn btn-secondary btn-sm w-full" onClick={handleConnect} disabled={connecting}>
          <KeyRound size={13} />
          {connecting ? t('home.connecting') : t('home.connectBlaze')}
        </button>
      )}
    </div>
  );
}

export function Sidebar() {
  return (
    <aside className="flex flex-col h-screen overflow-hidden flex-shrink-0 bg-bg-sidebar border-r border-border-default" style={{ width: 'var(--sidebar-width)' }} aria-label={t('sidebar.controlPanel')}>
      {/* Brand */}
      <div className="flex items-center gap-2.5 px-5 py-4 border-b border-border-default">
        <div className="flex items-center justify-center rounded-lg" style={{ width: 32, height: 32, background: 'linear-gradient(135deg, var(--primary), var(--accent))' }}>
          <Flame size={18} color="#fff" />
        </div>
        <div>
          <div className="text-[15px] font-bold tracking-tight text-text-primary">
            {t('app.title')}
          </div>
          <div className="text-[11px] text-text-muted">{t('sidebar.controlPanel')}</div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex flex-col gap-0.5 flex-1 py-3 px-2 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-2.5 px-3 py-[9px] rounded-lg no-underline text-[13px] font-medium transition-all duration-fast ${
                isActive
                  ? 'text-text-primary bg-primary-subtle'
                  : 'text-text-secondary bg-transparent hover:text-text-primary'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <item.icon size={16} className={isActive ? 'text-primary' : 'text-text-muted'} />
                <span>{t(item.labelKey)}</span>
                {isActive && (
                  <div className="ml-auto w-1.5 h-1.5 rounded-full bg-primary" />
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Account */}
      <AccountFooter />
    </aside>
  );
}
