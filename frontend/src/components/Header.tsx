import { usePolling } from '../hooks/usePolling';
import { getStatus } from '../api/client';
import { StatusDot } from './Badge';
import { RefreshCw, Globe, Sun, Moon } from 'lucide-react';
import { t, setLocale, getLocale } from '../i18n';
import { useTheme } from '../hooks/useTheme';

interface HeaderProps {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}

export function Header({ title, subtitle, actions }: HeaderProps) {
  const { data: status, reload } = usePolling(() => getStatus(), 15000);
  const isOnline = !!status;
  const { theme, toggleTheme } = useTheme();

  return (
    <header
      className="flex items-center justify-between px-6 flex-shrink-0 bg-bg-sidebar border-b border-border-default"
      style={{ height: 'var(--header-height)' }}
    >
      <div className="flex items-center gap-3">
        <h1 className="text-base font-semibold text-text-primary">{title}</h1>
        {subtitle && (
          <span className="text-xs text-text-muted">{subtitle}</span>
        )}
      </div>
      <div className="flex items-center gap-3">
        {actions}
        <StatusDot status={isOnline ? 'active' : 'error'} label={isOnline ? t('common.connected') : t('common.disconnected')} />
        <button
          className="btn btn-secondary btn-sm"
          onClick={toggleTheme}
          title={theme === 'dark' ? t('header.themeLight') : t('header.themeDark')}
          aria-label={theme === 'dark' ? t('header.themeLight') : t('header.themeDark')}
        >
          {theme === 'dark' ? <Sun size={14} /> : <Moon size={14} />}
        </button>
        <button className="btn btn-secondary btn-sm" onClick={() => reload()} title={t('header.refresh')} aria-label={t('header.refresh')}>
          <RefreshCw size={14} />
        </button>
        <button
          className="btn btn-secondary btn-sm min-w-[60px]"
          onClick={() => setLocale(getLocale() === 'pt-BR' ? 'en' : 'pt-BR')}
          title={t('header.langSwitch')}
          aria-label={t('header.langSwitch')}
        >
          <Globe size={14} />
          <span className="text-[11px] font-semibold">{getLocale() === 'pt-BR' ? 'PT-BR' : 'EN'}</span>
        </button>
      </div>
    </header>
  );
}
