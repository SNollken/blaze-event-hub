import { useState, useEffect } from 'react';
import { getEvents } from '../api/client';
import type { EventResponse } from '../api/client';

export default function MyEvents() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getEvents().then(setEvents).finally(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ padding: 40, color: 'var(--text-muted)' }}>Carregando...</div>;

  return (
    <div style={{ padding: '32px 40px', maxWidth: 860 }}>
      <h1 style={{ fontSize: 20, fontWeight: 510, color: 'var(--text-primary)', letterSpacing: '-0.3px', marginBottom: 28 }}>
        Meus eventos
      </h1>

      {events.length === 0 ? (
        <div style={{ padding: '60px 0', textAlign: 'center', color: 'var(--text-muted)', fontSize: 14 }}>
          Voce ainda nao criou nenhum evento
          <div style={{ marginTop: 12 }}>
            <a href="/events/create" style={{ color: 'var(--brand-light)', fontSize: 13 }}>Criar primeiro evento</a>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {events.map(ev => (
            <a key={ev.id} href={`/events/${ev.id}`} style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '12px 14px', background: 'var(--bg-card)',
              border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
              textDecoration: 'none', transition: 'border-color 0.12s',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--border-hover)')}
            onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border-card)')}
            >
              <div>
                <div style={{ fontSize: 14, fontWeight: 510, color: 'var(--text-primary)' }}>{ev.title}</div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                  {ev.description?.slice(0, 60) || 'Sem descricao'}
                </div>
              </div>
              <span style={{
                fontSize: 11, fontWeight: 510,
                color: ev.status === 'OPEN' ? 'var(--success)' : 'var(--text-muted)',
                background: ev.status === 'OPEN' ? 'var(--success-bg)' : 'rgba(255,255,255,0.04)',
                padding: '3px 10px', borderRadius: 'var(--radius-full)',
              }}>{ev.status}</span>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
