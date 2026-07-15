import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import {
  getMe,
  getOAuthSession,
  startOAuth,
  type MemberProfile,
  type OAuthSessionResponse,
} from '../api/client';
import { notifyAuthSessionChanged } from '../auth-session';
import { useI18n } from '../i18n/I18nContext';
import { toSafeOAuthUrl } from '../oauth-navigation';

const RETURN_TO_KEY = 'beh_return_to';

function safeReturnTo(value: unknown): string | null {
  if (typeof value !== 'string'
    || !value.startsWith('/')
    || value.startsWith('//')
    || value.startsWith('/\\')
    || /[\u0000-\u001F\u007F]/.test(value)) {
    return null;
  }

  try {
    const parsed = new URL(value, window.location.origin);
    if (parsed.origin !== window.location.origin || parsed.pathname === '/login') return null;
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return null;
  }
}

function readStoredReturnTo() {
  try {
    return safeReturnTo(window.sessionStorage.getItem(RETURN_TO_KEY));
  } catch {
    return null;
  }
}

function storeReturnTo(value: string | null) {
  try {
    if (value) window.sessionStorage.setItem(RETURN_TO_KEY, value);
    else window.sessionStorage.removeItem(RETURN_TO_KEY);
  } catch {
    // A navegação OAuth continua funcional mesmo com storage indisponível.
  }
}

export default function Login() {
  const location = useLocation();
  const { t } = useI18n();
  const oauthStatus = new URLSearchParams(location.search).get('oauth');
  const [session, setSession] = useState<OAuthSessionResponse | null>(null);
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState<'callback' | 'session' | 'start' | null>(
    () => oauthStatus === 'error' ? 'callback' : null,
  );

  const routeReturnTo = safeReturnTo((location.state as { from?: unknown } | null)?.from);
  const returnTo = routeReturnTo || readStoredReturnTo();

  useEffect(() => {
    if (routeReturnTo) storeReturnTo(routeReturnTo);
  }, [routeReturnTo]);

  useEffect(() => {
    let active = true;

    async function loadSession() {
      setLoading(true);

      try {
        const currentSession = await getOAuthSession();
        if (!active) return;
        setSession(currentSession);
        if (currentSession.connected) notifyAuthSessionChanged();
        if (currentSession.connected) setError(null);

        if (currentSession.connected) {
          try {
            const currentMember = await getMe();
            if (active) setMember(currentMember);
          } catch {
            if (active) setMember(null);
          }
        }
      } catch {
        if (active) {
          setError('session');
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadSession();
    return () => {
      active = false;
    };
  }, []);

  const handleConnect = async () => {
    setConnecting(true);
    setError(null);
    storeReturnTo(returnTo);

    try {
      const { authorizationUrl } = await startOAuth();
      window.location.assign(toSafeOAuthUrl(authorizationUrl));
    } catch {
      setError('start');
      setConnecting(false);
    }
  };

  const connected = session?.connected === true;
  const displayName = member?.displayName
    || session?.profile?.displayName
    || session?.profile?.username
    || t('loginCreatorFallback');
  const avatarUrl = member?.avatarUrl || session?.profile?.avatarUrl || null;
  const oauthSucceeded = new URLSearchParams(location.search).get('oauth') === 'success';
  const errorMessage = error === 'callback'
    ? t('loginCallbackError')
    : error === 'session'
      ? t('loginSessionError')
      : error === 'start'
        ? t('loginStartError')
        : '';

  if (!loading && connected && oauthSucceeded && returnTo) {
    storeReturnTo(null);
    return <Navigate to={returnTo} replace />;
  }

  return (
    <div className="hub-page login-center">
      <section className="login-card" aria-labelledby="login-title">
        {connected && avatarUrl && (
          <div className="login-logo login-logo-avatar">
            <img className="login-avatar" src={avatarUrl} alt="" />
          </div>
        )}

        <h1 id="login-title" className="page-title">{t('loginTitle')}</h1>
        <p className="login-headline">{t('loginDescription')}</p>

        {loading ? (
          <div className="empty" role="status">{t('loginChecking')}</div>
        ) : connected ? (
          <div className="login-connected">
            <p className="login-identity">{t('loginConnectedAs')} <strong>{displayName}</strong></p>
            <div className="login-actions">
              <Link className="btn btn-primary" to={returnTo || '/my-events'}>
                {returnTo ? t('loginContinue') : t('loginOpenMine')}
              </Link>
              <Link className="btn btn-secondary" to="/events">{t('loginExplore')}</Link>
            </div>
          </div>
        ) : (
          <div className="login-connect">
            {errorMessage && <div className="notice notice-danger" role="alert">{errorMessage}</div>}
            <button
              type="button"
              className="btn btn-primary"
              disabled={connecting}
              onClick={() => void handleConnect()}
            >
              {connecting ? t('loginOpening') : t('loginConnect')}
            </button>
          </div>
        )}

        <p className="login-footer">{t('loginPrivacy')}</p>
      </section>
    </div>
  );
}
