import { usePolling } from './Toast';
import { getStatus } from '../api/client';
import { StatusDot } from './Badge';
import { RefreshCw } from 'lucide-react';

interface HeaderProps {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export function Header({ title, subtitle, actions }: HeaderProps) {
  const { data: status, reload } = usePolling(() => getStatus(), 15000);

  const isOnline = !!status;

  return (
    <header
      style={{
        height: 'var(--header-height)',
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        flexShrink: 0,
        background: 'var(--bg-sidebar)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <h1 style={{ fontSize: 16, fontWeight: 600, color: 'var(--text-primary)' }}>{title}</h1>
        {subtitle && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{subtitle}</span>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {actions}
        <StatusDot status={isOnline ? 'active' : 'error'} label={isOnline ? 'Online' : 'Offline'} />
        <button className="btn btn-secondary btn-sm" onClick={() => reload()} title="Atualizar status">
          <RefreshCw size={14} />
        </button>
      </div>
    </header>
  );
}
