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
import { toSafeOAuthUrl } from '../oauth-navigation';

const RETURN_TO_KEY = 'beh_return_to';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

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
  const [session, setSession] = useState<OAuthSessionResponse | null>(null);
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState('');

  const routeReturnTo = safeReturnTo((location.state as { from?: unknown } | null)?.from);
  const returnTo = routeReturnTo || readStoredReturnTo();

  useEffect(() => {
    if (routeReturnTo) storeReturnTo(routeReturnTo);
  }, [routeReturnTo]);

  useEffect(() => {
    let active = true;

    async function loadSession() {
      setLoading(true);
      setError('');

      try {
        const currentSession = await getOAuthSession();
        if (!active) return;
        setSession(currentSession);
        if (currentSession.connected) notifyAuthSessionChanged();

        if (currentSession.connected) {
          try {
            const currentMember = await getMe();
            if (active) setMember(currentMember);
          } catch {
            if (active) setMember(null);
          }
        }
      } catch (sessionError) {
        if (active) {
          setError(getErrorMessage(sessionError, 'Não foi possível verificar sua sessão Blaze.'));
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
    setError('');
    storeReturnTo(returnTo);

    try {
      const { authorizationUrl } = await startOAuth();
      window.location.assign(toSafeOAuthUrl(authorizationUrl));
    } catch (connectError) {
      setError(getErrorMessage(connectError, 'Não foi possível iniciar a conexão com a Blaze.'));
      setConnecting(false);
    }
  };

  const connected = session?.connected === true;
  const displayName = member?.displayName
    || session?.profile?.displayName
    || session?.profile?.username
    || 'Criador Blaze';
  const avatarUrl = member?.avatarUrl || session?.profile?.avatarUrl || null;
  const oauthSucceeded = new URLSearchParams(location.search).get('oauth') === 'success';

  if (!loading && connected && oauthSucceeded && returnTo) {
    storeReturnTo(null);
    return <Navigate to={returnTo} replace />;
  }

  return (
    <div className="hub-page login-center">
      <section className="login-card" aria-labelledby="login-title">
        {connected && avatarUrl ? (
          <div className="login-logo login-logo-avatar">
            <img className="login-avatar" src={avatarUrl} alt="" />
          </div>
        ) : (
          <div className="login-logo" aria-hidden="true">BEH</div>
        )}

        <span className="page-eyebrow">Blaze Event Hub</span>
        <h1 id="login-title" className="page-title">Conecte sua conta Blaze</h1>
        <p className="login-headline">
          Crie giveaways, capture participantes pelo comando no chat e faça o sorteio somente depois de
          finalizar o evento.
        </p>

        {loading ? (
          <div className="empty" role="status">Verificando sua sessão…</div>
        ) : connected ? (
          <div className="login-connected">
            <p className="login-identity">Conta conectada: <strong>{displayName}</strong></p>
            <div className="login-actions">
              <Link className="btn btn-primary" to={returnTo || '/my-events'}>
                {returnTo ? 'Continuar de onde parei' : 'Abrir meus giveaways'}
              </Link>
              <Link className="btn btn-secondary" to="/events">Explorar giveaways</Link>
            </div>
          </div>
        ) : (
          <div className="login-connect">
            {error && <div className="notice notice-danger" role="alert">{error}</div>}
            <button
              type="button"
              className="btn btn-primary"
              disabled={connecting}
              onClick={() => void handleConnect()}
            >
              {connecting ? 'Abrindo a Blaze…' : 'Conectar com Blaze'}
            </button>
          </div>
        )}

        <p className="login-footer">
          A autenticação acontece na Blaze. Sua senha nunca passa pelo Blaze Event Hub.
        </p>
      </section>
    </div>
  );
}
