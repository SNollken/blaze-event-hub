import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getEvents } from '../api/client';
import type { EventResponse } from '../api/client';

const STATUS_MAP: Record<string, { pill: string; label: string }> = {
  OPEN:      { pill: 'pill--open',      label: 'Aberto' },
  CLOSED:    { pill: 'pill--closed',    label: 'Encerrado' },
  DRAWING:   { pill: 'pill--completed', label: 'Sorteando' },
  COMPLETED: { pill: 'pill--completed', label: 'Concluido' },
  CANCELLED: { pill: 'pill--cancelled', label: 'Cancelado' },
  DRAFT:     { pill: 'pill--draft',     label: 'Rascunho' },
};

export default function Events() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;

    async function loadEvents() {
      setLoading(true);
      setError(null);

      try {
        const apiEvents = await getEvents();
        if (alive) setEvents(apiEvents);
      } catch {
        if (alive) {
          setEvents([]);
          setError('Nao foi possivel carregar os eventos.');
        }
      } finally {
        if (alive) setLoading(false);
      }
    }

    loadEvents();

    return () => {
      alive = false;
    };
  }, []);

  const openEvents = events.filter((e) => e.status === 'OPEN');
  const closedEvents = events.filter((e) => e.status !== 'OPEN');
  const summary = loading
    ? 'Carregando eventos...'
    : error
      ? 'Falha ao carregar eventos'
      : `${events.length} evento${events.length !== 1 ? 's' : ''} encontrado${events.length !== 1 ? 's' : ''}`;

  return (
    <div style={{ padding: '32px 40px' }}>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
        marginBottom: 28,
      }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 600, color: 'var(--fg)', letterSpacing: '-0.3px', margin: 0 }}>
            Eventos
          </h1>
          <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>
            {summary}
          </p>
        </div>
        <Link to="/events/create" className="btn btn-primary">
          Criar evento
        </Link>
      </div>

      {loading && (
        <div className="empty">Carregando eventos...</div>
      )}

      {!loading && error && (
        <div className="empty" style={{ color: 'var(--danger)' }}>
          {error}
        </div>
      )}

      {!loading && !error && events.length === 0 ? (
        <div className="empty">
          Nenhum evento ainda
        </div>
      ) : null}

      {!loading && !error && events.length > 0 && (
        <>
          {openEvents.length > 0 && (
            <Section
              label="Abertos"
              count={openEvents.length}
              events={openEvents}
            />
          )}

          {closedEvents.length > 0 && (
            <Section
              label="Encerrados"
              count={closedEvents.length}
              events={closedEvents}
            />
          )}
        </>
      )}
    </div>
  );
}

function Section({ label, count, events }: { label: string; count: number; events: EventResponse[] }) {
  return (
    <div style={{ marginBottom: 32 }}>
      <div className="section-label">
        {label}
        <span className="count">{count}</span>
      </div>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
        gap: 12,
      }}>
        {events.map((ev) => (
          <EventCard key={ev.id} event={ev} />
        ))}
      </div>
    </div>
  );
}

function EventCard({ event }: { event: EventResponse }) {
  const status = STATUS_MAP[event.status] || STATUS_MAP.DRAFT;
  const rulesCount = event.rules?.length ?? 0;

  return (
    <Link
      to={`/events/${event.id}`}
      className="card"
      style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '14px 16px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span className={`pill ${status.pill}`}>{status.label}</span>
      </div>

      <div className="card-title" style={{ fontSize: 14 }}>
        {event.title}
      </div>

      <div className="card-desc" style={{ whiteSpace: 'normal', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
        {event.description?.slice(0, 120) || 'Sem descricao'}
      </div>

      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginTop: 'auto', paddingTop: 6,
        borderTop: '1px solid var(--border-card)',
        fontSize: 12, color: 'var(--muted)',
      }}>
        <span>
          {rulesCount > 0 ? `${rulesCount} regra${rulesCount > 1 ? 's' : ''}` : 'Sem regras'}
        </span>
        <span style={{ color: 'var(--accent-light)', fontWeight: 510 }}>
          Ver
        </span>
      </div>
    </Link>
  );
}