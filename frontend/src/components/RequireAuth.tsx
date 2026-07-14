import { useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { getOAuthSession } from '../api/client';
import { useI18n } from '../i18n/I18nContext';

type AuthStatus = 'loading' | 'ok' | 'denied';

/**
 * Route guard: only renders children when there is an active Blaze session.
 * Unauthenticated users are redirected to /login.
 * Uses getOAuthSession().connected (same signal the Sidebar/Login already use).
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const location = useLocation();
  const [status, setStatus] = useState<AuthStatus>('loading');

  useEffect(() => {
    let alive = true;
    getOAuthSession()
      .then((session) => {
        if (alive) setStatus(session.connected ? 'ok' : 'denied');
      })
      .catch(() => {
        if (alive) setStatus('denied');
      });
    return () => {
      alive = false;
    };
  }, []);

  if (status === 'loading') {
    return <div style={{ padding: 24, color: 'var(--muted)' }}>{t('checkingSession')}</div>;
  }

  if (status === 'denied') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}${location.hash}` }}
      />
    );
  }

  return <>{children}</>;
}
