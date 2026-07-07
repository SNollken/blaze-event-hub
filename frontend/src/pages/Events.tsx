import { useState, useEffect } from 'react';
import { getEvents } from '../api/client';
import type { EventResponse } from '../api/client';

export default function Events() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getEvents().then(setEvents).finally(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ padding: 40, color: 'var(--text-muted)' }}>Carregando...</div>;

  const open = events.filter(e => e.status === 'OPEN');
  const other = events.filter(e => e.status !== 'OPEN');

  return (
    <div style={{ padding: '32px 40px', maxWidth: 860 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 510, color: 'var(--text-primary)', letterSpacing: '-0.3px' }}>
            Eventos
          </h1>
          <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
            {events.length} evento{events.length !== 1 ? 's' : ''} encontrado{events.length !== 1 ? 's' : ''}
          </p>
        </div>
        <a href="/events/create" style={{
          background: 'var(--brand)', color: '#fff', border: 'none',
          padding: '7px 16px', borderRadius: 'var(--radius)',
          fontWeight: 510, fontSize: 13, cursor: 'pointer', textDecoration: 'none',
        }}>
          Criar evento
        </a>
      </div>

      {events.length === 0 ? (
        <div style={{ padding: '60px 0', textAlign: 'center', color: 'var(--text-muted)', fontSize: 14 }}>
          Nenhum evento ainda
        </div>
      ) : (
        <>
          {open.length > 0 && (
            <div style={{ marginBottom: 32 }}>
              <div style={{
                fontSize: 11, fontWeight: 510, color: 'var(--text-muted)',
                textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10,
              }}>
                Abertos ({open.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {open.map((ev) => <EventRow key={ev.id} event={ev} />)}
              </div>
            </div>
          )}
          {other.length > 0 && (
            <div>
              <div style={{
                fontSize: 11, fontWeight: 510, color: 'var(--text-muted)',
                textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10,
              }}>
                Encerrados ({other.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {other.map((ev) => <EventRow key={ev.id} event={ev} />)}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function EventRow({ event }: { event: EventResponse }) {
  const sc: Record<string, { c: string; bg: string; l: string }> = {
    OPEN: { c: 'var(--success)', bg: 'var(--success-bg)', l: 'Aberto' },
    CLOSED: { c: 'var(--warning)', bg: 'var(--warning-bg)', l: 'Fechado' },
    COMPLETED: { c: 'var(--brand-light)', bg: 'var(--brand-bg)', l: 'Concluido' },
    DRAFT: { c: 'var(--text-muted)', bg: 'rgba(255,255,255,0.04)', l: 'Rascunho' },
    CANCELLED: { c: 'var(--danger)', bg: 'var(--danger-bg)', l: 'Cancelado' },
  };
  const s = sc[event.status] || sc.DRAFT;

  return (
    <a href={`/events/${event.id}`} style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      padding: '12px 14px', background: 'var(--bg-card)',
      border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
      textDecoration: 'none', transition: 'border-color 0.12s',
    }}
    onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--border-hover)')}
    onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border-card)')}
    >
      <div>
        <div style={{ fontSize: 14, fontWeight: 510, color: 'var(--text-primary)' }}>{event.title}</div>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
          {event.description?.slice(0, 60) || 'Sem descricao'}
          {event.rules && event.rules.length > 0 && <> · {event.rules.length} regra{event.rules.length > 1 ? 's' : ''}</>}
        </div>
      </div>
      <span style={{
        fontSize: 11, fontWeight: 510, color: s.c, background: s.bg,
        padding: '3px 10px', borderRadius: 'var(--radius-full)',
      }}>
        {s.l}
      </span>
    </a>
  );
}
