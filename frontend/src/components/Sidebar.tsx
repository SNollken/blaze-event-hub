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

const navItems = [
  { to: '/', label: 'Visao Geral', icon: LayoutDashboard },
  { to: '/events', label: 'Eventos ao Vivo', icon: Radio },
  { to: '/blaze', label: 'Blaze Channel', icon: RadioTower },
  { to: '/alerts', label: 'Alertas', icon: Bell },
  { to: '/giveaways', label: 'Sorteios', icon: Gift },
  { to: '/overlays', label: 'Overlays', icon: Layers },
];

export function Sidebar() {
  return (
    <aside
      style={{
        width: 'var(--sidebar-width)',
        flexShrink: 0,
        background: 'var(--bg-sidebar)',
        borderRight: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        overflow: 'hidden',
      }}
    >
      {/* Brand */}
      <div
        style={{
          padding: '16px 20px',
          borderBottom: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
        }}
      >
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            background: 'linear-gradient(135deg, var(--primary), var(--accent))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Flame size={18} color="#fff" />
        </div>
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: '-0.02em', color: 'var(--text-primary)' }}>
            NollenBlaze
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Painel de Controle</div>
        </div>
      </div>

      {/* Navigation */}
      <nav style={{ flex: 1, padding: '12px 8px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 2 }}>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '9px 12px',
              borderRadius: 'var(--radius)',
              textDecoration: 'none',
              fontSize: 13,
              fontWeight: 500,
              color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
              background: isActive ? 'var(--primary-subtle)' : 'transparent',
              transition: 'all var(--transition-fast)',
            })}
          >
            {({ isActive }) => (
              <>
                <item.icon size={16} style={{ color: isActive ? 'var(--primary)' : 'var(--text-muted)' }} />
                <span>{item.label}</span>
                {isActive && (
                  <div
                    style={{
                      marginLeft: 'auto',
                      width: 6,
                      height: 6,
                      borderRadius: '50%',
                      background: 'var(--primary)',
                    }}
                  />
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div
        style={{
          padding: '12px 16px',
          borderTop: '1px solid var(--border)',
          fontSize: 11,
          color: 'var(--text-muted)',
          textAlign: 'center',
        }}
      >
        NollenBlaze v0.1
      </div>
    </aside>
  );
}
