import { useState, useEffect } from 'react';
import { getStatus, getOAuthSession, startOAuth, getEvents } from '../api/client';
import type { StatusResponse, OAuthSessionResponse, EventResponse } from '../api/client';
import { StatsCard } from '../components/StatsCard';

export default function Dashboard() {
  const [status, setStatus] = useState<StatusResponse | null>(null);
  const [oauth, setOAuth] = useState<OAuthSessionResponse | null>(null);
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([getStatus(), getOAuthSession(), getEvents('OPEN')])
      .then(([s, o, e]) => { setStatus(s); setOAuth(o); setEvents(e); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const handleConnect = async () => {
    try {
      const { authorizationUrl } = await startOAuth();
      window.location.href = authorizationUrl;
    } catch (e: any) {
      setError(e.message);
    }
  };

  if (loading) return <div style={{ padding: 24 }}>Carregando...</div>;
  if (error) return <div style={{ padding: 24, color: 'var(--danger)' }}>Erro: {error}</div>;

  return (
    <div style={{ padding: 24 }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Blaze Event Hub</h1>

      {!oauth?.connected && (
        <div style={{ background: 'var(--bg-hover)', padding: 20, borderRadius: 12, marginBottom: 24, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>Conecte sua conta Blaze para criar e participar de eventos.</span>
          <button onClick={handleConnect} style={{
            background: 'var(--primary)', color: '#fff', border: 'none',
            padding: '10px 20px', borderRadius: 8, cursor: 'pointer', fontWeight: 600,
          }}>
            Conectar Blaze
          </button>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16, marginBottom: 24 }}>
        <StatsCard title="Status" value={status?.appName ?? '-'} subtitle={`v${status?.version ?? '?'}`} />
        <StatsCard title="Blaze" value={oauth?.connected ? 'Conectado' : 'Desconectado'} subtitle={oauth?.connected ? (status?.connectedAccountDisplayName ?? 'OK') : 'Não conectado'} />
        <StatsCard title="Eventos Abertos" value={String(events.length)} subtitle="Aguardando participantes" />
        <StatsCard title="Uptime" value={status ? `${Math.floor(status.uptimeSeconds / 60)}min` : '-'} subtitle="Java 21 + Spring Boot" />
      </div>

      <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 16 }}>Eventos Abertos</h2>
      {events.length === 0 ? (
        <p style={{ color: 'var(--text-secondary)' }}>Nenhum evento aberto no momento.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {events.map((ev) => (
            <div key={ev.id} style={{ background: 'var(--bg-card)', padding: 16, borderRadius: 10, border: '1px solid var(--border)' }}>
              <a href={`/events/${ev.id}`} style={{ fontSize: 16, fontWeight: 600, color: 'var(--primary)', textDecoration: 'none' }}>
                {ev.title}
              </a>
              <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>{ev.description?.slice(0, 100) || 'Sem descrição'}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
