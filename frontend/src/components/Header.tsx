import { usePolling } from '../hooks/usePolling';
import { getStatus } from '../api/client';
import { StatusDot } from './Badge';
import { RefreshCw, Globe } from 'lucide-react';
import { t, setLocale, getLocale } from '../i18n';

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
        <StatusDot status={isOnline ? 'active' : 'error'} label={isOnline ? t('common.connected') : t('common.disconnected')} />
        <button className="btn btn-secondary btn-sm" onClick={() => reload()} title={t('header.refresh')} aria-label={t('header.refresh')}>
          <RefreshCw size={14} />
        </button>
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => setLocale(getLocale() === 'pt-BR' ? 'en' : 'pt-BR')}
          title={t('header.langSwitch')}
          aria-label={t('header.langSwitch')}
          style={{ minWidth: 60 }}
        >
          <Globe size={14} />
          <span style={{ fontSize: 11, fontWeight: 600 }}>{getLocale() === 'pt-BR' ? 'PT-BR' : 'EN'}</span>
        </button>
      </div>
    </header>
  );
}