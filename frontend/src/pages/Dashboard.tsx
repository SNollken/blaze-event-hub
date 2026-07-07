import { useState, useEffect } from 'react';
import { getEvents, getOAuthSession, startOAuth } from '../api/client';
import type { EventResponse, OAuthSessionResponse } from '../api/client';

export default function Dashboard() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [oauth, setOAuth] = useState<OAuthSessionResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getEvents('OPEN'), getOAuthSession()])
      .then(([e, o]) => { setEvents(e); setOAuth(o); })
      .finally(() => setLoading(false));
  }, []);

  const handleConnect = async () => {
    try {
      const { authorizationUrl } = await startOAuth();
      window.location.href = authorizationUrl;
    } catch {}
  };

  if (loading) return <div style={{ padding: 40, color: 'var(--text-muted)' }}>Carregando...</div>;

  const connected = oauth?.connected;

  return (
    <div style={{ padding: '32px 40px', maxWidth: 860 }}>
      {/* Header */}
      <div style={{ marginBottom: 32 }}>
        <h1 style={{
          fontSize: 24, fontWeight: 510, color: 'var(--text-primary)',
          letterSpacing: '-0.5px', marginBottom: 6,
        }}>
          Blaze Event Hub
        </h1>
        <p style={{ fontSize: 14, color: 'var(--text-muted)' }}>
          Eventos comunitarios para a Blaze stream
        </p>
      </div>

      {/* Connect CTA */}
      {!connected && (
        <div style={{
          background: 'var(--brand-bg)', border: '1px solid rgba(94,106,210,0.2)',
          borderRadius: 'var(--radius-lg)', padding: '20px 24px', marginBottom: 32,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16,
        }}>
          <div>
            <div style={{ fontSize: 14, fontWeight: 510, color: 'var(--text-primary)', marginBottom: 4 }}>
              Conecte sua conta Blaze
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>
              Necessario para criar eventos e participar de giveaways
            </div>
          </div>
          <button onClick={handleConnect} style={{
            background: 'var(--brand)', color: '#fff', border: 'none',
            padding: '8px 18px', borderRadius: 'var(--radius)', fontWeight: 510, fontSize: 13, cursor: 'pointer',
          }}>
            Conectar
          </button>
        </div>
      )}

      {/* Events */}
      <div>
        <div style={{
          fontSize: 12, fontWeight: 510, color: 'var(--text-muted)',
          textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 12,
        }}>
          Eventos Abertos
        </div>

        {events.length === 0 ? (
          <div style={{ padding: '48px 0', textAlign: 'center', color: 'var(--text-muted)', fontSize: 14 }}>
            Nenhum evento aberto no momento
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {events.map((ev) => (
              <a
                key={ev.id}
                href={`/events/${ev.id}`}
                style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '14px 16px', background: 'var(--bg-card)',
                  border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
                  textDecoration: 'none', transition: 'border-color 0.12s',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--border-hover)')}
                onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border-card)')}
              >
                <div>
                  <div style={{ fontSize: 14, fontWeight: 510, color: 'var(--text-primary)' }}>
                    {ev.title}
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
                    {ev.description?.slice(0, 80) || 'Sem descricao'}
                  </div>
                </div>
                <span style={{
                  fontSize: 11, fontWeight: 510, color: 'var(--success)',
                  background: 'var(--success-bg)', padding: '3px 10px', borderRadius: 'var(--radius-full)',
                }}>
                  Aberto
                </span>
              </a>
            ))}
          </div>
        )}
      </div>

      {/* Quick actions */}
      {connected && (
        <div style={{ marginTop: 32 }}>
          <a href="/events/create" style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '8px 16px', borderRadius: 'var(--radius)',
            border: '1px solid var(--border-card)', background: 'var(--bg-button)',
            color: 'var(--text-secondary)', fontSize: 13, fontWeight: 510,
            textDecoration: 'none', transition: 'border-color 0.12s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--border-hover)')}
          onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border-card)')}
          >
            Criar evento
          </a>
        </div>
      )}
    </div>
  );
}
