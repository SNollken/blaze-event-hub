import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  CalendarPlus,
  ListTodo,
  Trophy,
  User,
} from 'lucide-react';

const navItems = [
  { to: '/', label: 'Visão Geral', icon: LayoutDashboard },
  { to: '/events', label: 'Eventos', icon: ListTodo },
  { to: '/events/create', label: 'Criar Evento', icon: CalendarPlus },
  { to: '/my-events', label: 'Meus Eventos', icon: User },
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
          <Trophy size={18} color="#fff" />
        </div>
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: '-0.02em', color: 'var(--text-primary)' }}>
            Blaze Event Hub
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', letterSpacing: '0.02em' }}>
            Eventos Comunitários
          </div>
        </div>
      </div>

      <nav style={{ padding: '12px 8px', flex: 1 }}>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '10px 12px',
              borderRadius: 8,
              marginBottom: 2,
              textDecoration: 'none',
              fontSize: 14,
              fontWeight: isActive ? 600 : 400,
              color: isActive ? 'var(--primary)' : 'var(--text-secondary)',
              background: isActive ? 'var(--bg-hover)' : 'transparent',
              transition: 'all 0.15s',
            })}
          >
            <item.icon size={18} />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border)', fontSize: 11, color: 'var(--text-secondary)' }}>
        Blaze Event Hub v0.1.0
      </div>
    </aside>
  );
}
