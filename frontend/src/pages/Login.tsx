import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMe, getOAuthSession, startOAuth } from '../api/client';
import type { MemberProfile, OAuthSessionResponse } from '../api/client';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

export default function Login() {
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
        if (alive) setError(getErrorMessage(err, 'Nao foi possivel verificar a sessao Blaze.'));
      } finally {
        if (alive) setLoading(false);
      }
    }

    loadSession();

    return () => {
      alive = false;
    };
  }, []);

  const handleConnect = async () => {
    setConnecting(true);
    setError('');

    try {
      const { authorizationUrl } = await startOAuth();
      window.location.href = authorizationUrl;
    } catch (err) {
      setError(getErrorMessage(err, 'Nao foi possivel iniciar o OAuth Blaze.'));
      setConnecting(false);
    }
  };

  const connected = session?.connected === true;
  const displayName = member?.displayName || session?.profile?.displayName || session?.profile?.username || 'Conta Blaze conectada';
  const avatarUrl = member?.avatarUrl || session?.profile?.avatarUrl || null;
  const nextAction = session?.nextRecommendedAction;

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      background: 'var(--bg-deep)',
      padding: 24,
    }}>
      <div className="card" style={{ maxWidth: 460, width: '100%', padding: '36px 40px', textAlign: 'center' }}>
        <div style={{
          width: 56,
          height: 56,
          background: 'var(--accent-bg)',
          borderRadius: 'var(--r-lg)',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 20,
          color: 'var(--accent-light)',
          overflow: 'hidden',
        }}>
          {avatarUrl ? (
            <img src={avatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
              <path d="M15 3h4a2 2 0 012 2v14a2 2 0 01-2 2h-4M10 17l5-5-5-5M15 12H3" />
            </svg>
          )}
        </div>

        <h1 style={{ fontSize: 20, fontWeight: 600, color: 'var(--fg)', marginBottom: 8 }}>
          {connected ? displayName : 'Conectar com Blaze'}
        </h1>

        {loading ? (
          <div className="empty" style={{ padding: '16px 0' }}>Verificando sessao...</div>
        ) : (
          <>
            <p style={{ fontSize: 13, color: 'var(--muted)', marginBottom: 24, lineHeight: 1.55 }}>
              {connected
                ? 'Sessao Blaze ativa. Use o hub para criar eventos, acompanhar participacao e executar sorteios.'
                : 'Faca login com sua conta Blaze.stream para criar eventos, participar de giveaways e gerenciar suas inscricoes.'}
            </p>

            <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginBottom: 24, flexWrap: 'wrap' }}>
              <span className={`pill ${connected ? 'pill--open' : 'pill--draft'}`}>
                {connected ? 'Conectado' : 'Desconectado'}
              </span>
              {session?.profilePresent && <span className="pill pill--completed">Perfil sincronizado</span>}
            </div>

            {nextAction && (
              <div className="form-helper" style={{ justifyContent: 'center', marginBottom: 16 }}>
                {nextAction}
              </div>
            )}

            {error && (
              <div className="toast toast-error" style={{ position: 'static', marginBottom: 16, textAlign: 'left' }}>
                {error}
              </div>
            )}

            {connected ? (
              <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
                <Link to="/" className="btn btn-primary">Dashboard</Link>
                <Link to="/events" className="btn btn-secondary">Eventos</Link>
                <Link to="/my-events" className="btn btn-secondary">Meus eventos</Link>
              </div>
            ) : (
              <button
                onClick={handleConnect}
                className="btn btn-primary"
                disabled={connecting}
                style={{ width: '100%', padding: '12px', fontSize: 14 }}
              >
                {connecting ? 'Redirecionando...' : 'Conectar com Blaze.stream'}
              </button>
            )}
          </>
        )}

        <div style={{ marginTop: 16, fontSize: 11, color: 'var(--muted2)' }}>
          Tokens e credenciais nao sao exibidos nesta interface.
        </div>
      </div>
    </div>
  );
}
