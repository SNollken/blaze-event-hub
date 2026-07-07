import { NavLink } from 'react-router-dom';
import { LayoutDashboard, PlusCircle, Calendar } from 'lucide-react';

const navItems = [
  { to: '/', label: 'Home', icon: LayoutDashboard },
  { to: '/events/create', label: 'Novo Evento', icon: PlusCircle },
  { to: '/events', label: 'Todos os Eventos', icon: Calendar },
];

export function Sidebar() {
  return (
    <aside style={{
      width: 'var(--sidebar-width)',
      flexShrink: 0,
      background: 'var(--bg-sidebar)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      overflow: 'hidden',
    }}>
      <div style={{ padding: '18px 18px 14px', borderBottom: '1px solid var(--border)' }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--text-primary)', letterSpacing: '-0.03em' }}>
          Blaze Event Hub
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
          Eventos da comunidade Blaze
        </div>
      </div>

      <nav style={{ padding: '10px 10px', flex: 1 }}>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: 9,
              padding: '9px 12px',
              borderRadius: 'var(--radius)',
              marginBottom: 2,
              textDecoration: 'none',
              fontSize: 13,
              fontWeight: 500,
              color: isActive ? 'var(--text-primary)' : 'var(--text-tertiary)',
              background: isActive ? 'var(--bg-surface)' : 'transparent',
              transition: 'all 0.15s',
            })}
          >
            <item.icon size={16} style={{ opacity: 0.6 }} />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div style={{
        padding: '12px 18px',
        borderTop: '1px solid var(--border)',
        fontSize: 11,
        color: 'var(--text-muted)',
        fontFamily: 'var(--font-mono)',
      }}>
        v0.1.0
      </div>
    </aside>
  );
}
