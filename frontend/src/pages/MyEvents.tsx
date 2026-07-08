import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyEventHistory, getOAuthSession } from '../api/client';
import type { EventHistoryResponse, EventResponse } from '../api/client';

const STATUS_MAP: Record<string, { cls: string; label: string }> = {
  OPEN: { cls: 'pill--open', label: 'Aberto' },
  CLOSED: { cls: 'pill--closed', label: 'Fechado' },
  DRAWING: { cls: 'pill--completed', label: 'Sorteando' },
  COMPLETED: { cls: 'pill--completed', label: 'Concluido' },
  DRAFT: { cls: 'pill--draft', label: 'Rascunho' },
  CANCELLED: { cls: 'pill--cancelled', label: 'Cancelado' },
};

const emptyHistory: EventHistoryResponse = {
  drafts: [],
  upcoming: [],
  past: [],
};

function EventCard({ event }: { event: EventResponse }) {
  const status = STATUS_MAP[event.status] || STATUS_MAP.DRAFT;

  return (
    <Link
      key={event.id}
      to={`/events/${event.id}`}
      className="card"
      style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '18px 20px', minHeight: 140 }}
    >
      <span className={`pill ${status.cls}`}>{status.label}</span>
      <div
        className="card-title"
        style={{
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {event.title}
      </div>
      <div
        className="card-desc"
        style={{
          lineHeight: 1.45,
          whiteSpace: 'normal',
          maxWidth: 'none',
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
        }}
      >
        {event.description?.slice(0, 80) || 'Sem descricao'}
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: 'auto',
          paddingTop: 10,
          borderTop: '1px solid var(--border-card)',
          fontSize: 11,
          color: 'var(--muted)',
          fontFamily: 'var(--font-mono)',
          letterSpacing: '0.02em',
        }}
      >
        <span>{event.rules?.length || 0} regras / {event.participantCount || 0} part.</span>
        <span style={{ color: 'var(--accent-light)' }}>Ver</span>
      </div>
    </Link>
  );
}

function HistorySection({ title, events, emptyText }: { title: string; events: EventResponse[]; emptyText: string }) {
  return (
    <section style={{ marginBottom: 32 }}>
      <div className="section-label">
        {title} <span className="count">{events.length}</span>
      </div>
      {events.length === 0 ? (
        <div className="empty" style={{ padding: '24px 0' }}>{emptyText}</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 12 }}>
          {events.map((event) => <EventCard key={event.id} event={event} />)}
        </div>
      )}
    </section>
  );
}

export default function MyEvents() {
  const [history, setHistory] = useState<EventHistoryResponse>(emptyHistory);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [requiresLogin, setRequiresLogin] = useState(false);

  useEffect(() => {
    let alive = true;

    async function loadHistory() {
      setLoading(true);
      setError('');
      setRequiresLogin(false);

      try {
        const session = await getOAuthSession();
        if (!alive) return;

        if (!session.connected) {
          setHistory(emptyHistory);
          setRequiresLogin(true);
          return;
        }

        const response = await getMyEventHistory();
        if (alive) setHistory(response);
      } catch (err) {
        if (alive) {
          setHistory(emptyHistory);
          setError(err instanceof Error ? err.message : 'Nao foi possivel carregar seus eventos.');
        }
      } finally {
        if (alive) setLoading(false);
      }
    }

    loadHistory();

    return () => {
      alive = false;
    };
  }, []);

  const totalEvents = useMemo(
    () => history.drafts.length + history.upcoming.length + history.past.length,
    [history],
  );

  if (loading) return <div className="empty">Carregando meus eventos...</div>;

  return (
    <div style={{ padding: '32px 40px', maxWidth: 960 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28, gap: 16 }}>
        <div>
          <h1 className="page-title">Meus eventos</h1>
          <p className="page-subtitle" style={{ marginBottom: 0 }}>
            {totalEvents} evento{totalEvents !== 1 ? 's' : ''} criado{totalEvents !== 1 ? 's' : ''}
          </p>
        </div>
        <Link to="/events/create" className="btn btn-primary">+ Criar evento</Link>
      </div>

      {error && (
        <div className="empty" style={{ color: 'var(--danger)' }}>
          {error}
          <div style={{ marginTop: 12 }}>
            <Link to="/login" className="btn btn-secondary">Conectar Blaze</Link>
          </div>
        </div>
      )}

      {!error && requiresLogin && (
        <div className="empty">
          Conecte sua conta Blaze para ver seus eventos.
          <div style={{ marginTop: 12 }}>
            <Link to="/login" className="btn btn-secondary">Conectar Blaze</Link>
          </div>
        </div>
      )}

      {!error && !requiresLogin && totalEvents === 0 && (
        <div className="empty">
          Voce ainda nao criou nenhum evento
          <div style={{ marginTop: 12 }}>
            <Link to="/events/create" style={{ color: 'var(--accent-light)', fontSize: 13 }}>Criar primeiro evento</Link>
          </div>
        </div>
      )}

      {!error && !requiresLogin && totalEvents > 0 && (
        <>
          <HistorySection title="Rascunhos" events={history.drafts} emptyText="Nenhum rascunho." />
          <HistorySection title="Proximos" events={history.upcoming} emptyText="Nenhum evento ativo ou futuro." />
          <HistorySection title="Passados" events={history.past} emptyText="Nenhum evento passado." />
        </>
      )}
    </div>
  );
}
