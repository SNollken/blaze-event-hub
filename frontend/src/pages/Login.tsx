import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nContext';
import { getMe, getOAuthSession, startOAuth } from '../api/client';
import type { MemberProfile, OAuthSessionResponse } from '../api/client';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

export default function Login() {
  const { t } = useI18n();
  const [session, setSession] = useState<OAuthSessionResponse | null>(null);
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;

    async function loadSession() {
      setLoading(true);
      setError('');

      try {
        const currentSession = await getOAuthSession();
        if (!alive) return;
        setSession(currentSession);

        if (currentSession.connected && currentSession.profilePresent) {
          try {
            const profile = await getMe();
            if (alive) setMember(profile);
          } catch {
            if (alive) setMember(null);
          }
        }
      } catch (err) {
        if (alive) setError(getErrorMessage(err, t('sessionCheckError')));
      } finally {
        if (alive) setLoading(false);
      }
    }

    void loadSession();
    return () => {
      alive = false;
    };
  }, [t]);

  const handleConnect = async () => {
    setConnecting(true);
    setError('');

    try {
      const { authorizationUrl } = await startOAuth();
      window.location.href = authorizationUrl;
    } catch (err) {
      setError(getErrorMessage(err, t('loginOAuthStartError')));
      setConnecting(false);
    }
  };

  const connected = session?.connected === true;
  const displayName = member?.displayName
    || session?.profile?.displayName
    || session?.profile?.username
    || '';
  const avatarUrl = member?.avatarUrl
    || session?.profile?.avatarUrl
    || null;

  return (
    <div className="login-center">
      <div className="login-card">
        {connected && avatarUrl ? (
          <div className="login-logo" style={{ padding: 0, overflow: 'hidden' }}>
            <img src={avatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </div>
        ) : (
          <div className="login-logo">BEH</div>
        )}

        <h2>{t('loginTitle')}</h2>
        <div className="login-headline">{t('loginHeadline')}</div>

        {loading ? (
          <div className="empty">{t('checkingSession')}</div>
        ) : connected ? (
          <>
            {displayName && (
              <div style={{ marginBottom: 20, fontSize: 14, fontWeight: 600, color: 'var(--fg2)' }}>
                {displayName}
              </div>
            )}

            <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
              <Link to="/" className="btn btn-primary">{t('navDashboard')}</Link>
              <Link to="/events" className="btn btn-secondary">{t('navAllEvents')}</Link>
              <Link to="/my-events" className="btn btn-secondary">{t('navMyEvents')}</Link>
            </div>
          </>
        ) : (
          <>
            {error && (
              <div className="toast toast-error" style={{ position: 'static', marginBottom: 16, textAlign: 'left' }}>
                {error}
              </div>
            )}

            <button onClick={handleConnect} className="btn btn-primary" disabled={connecting}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path d="M15 3h4a2 2 0 012 2v14a2 2 0 01-2 2h-4" />
                <polyline points="10 17 15 12 10 7" />
                <line x1="15" y1="12" x2="3" y2="12" />
              </svg>
              {connecting ? t('connecting') : t('loginBtn')}
            </button>
          </>
        )}

        <div className="login-footer">{t('loginFooter')}</div>
      </div>
    </div>
  );
}
