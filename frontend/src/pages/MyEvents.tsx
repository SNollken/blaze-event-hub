import { useState, useEffect } from 'react';
import { getEvents } from '../api/client';
import type { EventResponse } from '../api/client';

export default function MyEvents() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getEvents()
      .then((all) => setEvents(all))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ padding: 24 }}>Carregando...</div>;
  if (error) return <div style={{ padding: 24, color: 'var(--danger)' }}>Erro: {error}</div>;

  return (
    <div style={{ padding: 24 }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Meus Eventos</h1>

      {events.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 60, color: 'var(--text-secondary)' }}>
          <p style={{ fontSize: 18, marginBottom: 8 }}>Você ainda não criou nenhum evento</p>
          <a href="/events/create" style={{ color: 'var(--primary)' }}>Criar meu primeiro evento</a>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {events.map((ev) => (
            <div key={ev.id} style={{ background: 'var(--bg-card)', padding: 16, borderRadius: 10, border: '1px solid var(--border)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <a href={`/events/${ev.id}`} style={{ fontSize: 16, fontWeight: 600, color: 'var(--primary)', textDecoration: 'none' }}>
                  {ev.title}
                </a>
                <span style={{ padding: '4px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600, background: 'var(--bg-hover)', color: 'var(--text-secondary)' }}>
                  {ev.status}
                </span>
              </div>
              <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
                Criado em: {ev.startsAt ? new Date(ev.startsAt).toLocaleDateString('pt-BR') : '--'}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
