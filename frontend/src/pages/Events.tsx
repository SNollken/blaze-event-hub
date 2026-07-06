import { useState, useEffect } from 'react';
import { getEvents } from '../api/client';
import type { EventResponse } from '../api/client';

export default function Events() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getEvents()
      .then(setEvents)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ padding: 24 }}>Carregando eventos...</div>;
  if (error) return <div style={{ padding: 24, color: 'var(--danger)' }}>Erro: {error}</div>;

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700 }}>Eventos</h1>
        <a href="/events/create" style={{
          background: 'var(--primary)', color: '#fff', textDecoration: 'none',
          padding: '10px 20px', borderRadius: 8, fontWeight: 600, fontSize: 14,
        }}>
          + Criar Evento
        </a>
      </div>

      {events.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 60, color: 'var(--text-secondary)' }}>
          <p style={{ fontSize: 18, marginBottom: 8 }}>Nenhum evento ainda</p>
          <p>Crie o primeiro evento ou aguarde novos eventos da comunidade.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {events.map((ev) => (
            <div key={ev.id} style={{
              background: 'var(--bg-card)', padding: 16, borderRadius: 10,
              border: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            }}>
              <div>
                <a href={`/events/${ev.id}`} style={{ fontSize: 16, fontWeight: 600, color: 'var(--primary)', textDecoration: 'none' }}>
                  {ev.title}
                </a>
                <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
                  {ev.description?.slice(0, 120) || 'Sem descrição'} &middot; Status: {ev.status}
                </p>
              </div>
              <span style={{
                padding: '4px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600,
                background: ev.status === 'OPEN' ? 'var(--success)' : 'var(--bg-hover)',
                color: ev.status === 'OPEN' ? '#fff' : 'var(--text-secondary)',
              }}>
                {ev.status === 'OPEN' ? 'Aberto' : ev.status}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
