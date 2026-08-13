import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Radio,
  RadioTower,
  Bell,
  Gift,
  Layers,
  Flame,
} from 'lucide-react';
import { t } from '../i18n';

const navItems = [
  { to: '/', labelKey: 'nav.dashboard', icon: LayoutDashboard },
  { to: '/events', labelKey: 'nav.events', icon: Radio },
  { to: '/blaze', labelKey: 'nav.blaze', icon: RadioTower },
  { to: '/alerts', labelKey: 'nav.alerts', icon: Bell },
  { to: '/giveaways', labelKey: 'nav.giveaways', icon: Gift },
  { to: '/overlays', labelKey: 'nav.overlays', icon: Layers },
];

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

      {/* Footer */}
      <div className="px-4 py-3 border-t border-border-default text-[11px] text-text-muted text-center">
        {t('app.title')}
      </div>
    </aside>
  );
}
