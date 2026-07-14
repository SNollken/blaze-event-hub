import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, Radio, Trophy } from 'lucide-react';
import { getEvents } from '../api/client';
import type { EventResponse, EventStatus } from '../api/types';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';

type PublicFilter = 'ALL' | Extract<EventStatus, 'OPEN' | 'CLOSED' | 'COMPLETED'>;

const STATUS_META: Record<EventStatus, { label: string; className: string }> = {
  DRAFT: { label: 'Rascunho', className: 'status-pill--draft' },
  OPEN: { label: 'Captando', className: 'status-pill--open' },
  FINALIZING: { label: 'Finalizando entradas', className: 'status-pill--finalizing' },
  CLOSED: { label: 'Finalizado', className: 'status-pill--closed' },
  COMPLETED: { label: 'Sorteado', className: 'status-pill--completed' },
  CANCELLED: { label: 'Cancelado', className: 'status-pill--cancelled' },
};

const FILTERS: { value: PublicFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'OPEN', label: 'Captando agora' },
  { value: 'CLOSED', label: 'Prontos para sorteio' },
  { value: 'COMPLETED', label: 'Sorteados' },
];

function formatDate(value: string | null) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(date);
}

function EventCard({ event }: { event: EventResponse }) {
  const status = STATUS_META[event.status];
  const endsAt = formatDate(event.endsAt);

  return (
    <Link to={`/events/${event.id}`} className={`event-card event-card--${event.status.toLowerCase()}`}>
      <div className="event-card__topline">
        <span className={`status-pill ${status.className}`}>{status.label}</span>
        {event.status === 'OPEN' && (
          <span className="event-card__signal" aria-label="Captação ativa"><span aria-hidden="true" /> ao vivo</span>
        )}
      </div>

      <div className="event-card__body">
        {event.creatorChannelSlug && (
          <span className="event-card__creator">
            {event.creatorChannelDisplayName || event.creatorChannelSlug} · @{event.creatorChannelSlug}
          </span>
        )}
        <p className="event-card__prize">{event.prize || 'Prêmio a confirmar'}</p>
        <h2 className="event-card__title">{event.title}</h2>
        <p className="event-card__description">
          {event.description || 'Confira o comando e os detalhes definidos pelo criador.'}
        </p>
      </div>

      {event.status === 'OPEN' ? (
        <div className="event-card__command">
          <span>Comando no chat</span>
          <code>{event.entryCommand || '!participar'}</code>
        </div>
      ) : event.status === 'FINALIZING' ? (
        <div className="event-card__meta">
          <Radio size={15} aria-hidden="true" />
          <span>Última sincronização em andamento</span>
        </div>
      ) : (
        <div className="event-card__meta">
          <Trophy size={15} aria-hidden="true" />
          <span>{event.finalizedParticipantCount} participantes no pool final</span>
        </div>
      )}

      <footer className="event-card__footer">
        <span>
          {endsAt ? <><CalendarDays size={15} aria-hidden="true" /> {endsAt}</> : 'Sem horário de término'}
        </span>
        <span className="event-card__link">Detalhes <ArrowRight size={15} aria-hidden="true" /></span>
      </footer>
    </Link>
  );
}

export default function Events() {
  const { t } = useI18n();
  const [filter, setFilter] = useState<PublicFilter>('ALL');
  const fetchEvents = useCallback(() => getEvents(), []);
  const eventsState = usePolling(fetchEvents, 10_000);
  const events = eventsState.data || [];
  const loading = eventsState.loading && !eventsState.data;
  const error = Boolean(eventsState.error && !eventsState.data);

  const publicEvents = useMemo(
    () => events.filter((event) => event.status !== 'DRAFT' && event.status !== 'CANCELLED'),
    [events],
  );

  const filteredEvents = useMemo(
    () => filter === 'ALL' ? publicEvents : publicEvents.filter((event) => event.status === filter),
    [filter, publicEvents],
  );

  const statusCount = (status: PublicFilter) => (
    status === 'ALL' ? publicEvents.length : publicEvents.filter((event) => event.status === status).length
  );

  return (
    <div className="page hub-page hub-page--events">
      <header className="page-hero page-hero--compact">
        <div className="page-hero__copy">
          <p className="page-eyebrow">Exploração pública</p>
          <h1 className="page-title">{t('eventsTitle')}</h1>
          <p className="page-subtitle">
            Encontre giveaways captando pelo chat, acompanhe os pools finalizados e veja os sorteios concluídos.
          </p>
        </div>
        <Link to="/events/create" className="btn btn-primary">Criar giveaway</Link>
      </header>

      {eventsState.error && eventsState.data && (
        <div className="notice notice--warning" role="status">
          A atualização automática falhou. A lista abaixo mostra os últimos dados recebidos.
        </div>
      )}

      <section className="hub-section" aria-labelledby="events-list-heading">
        <div className="section-heading section-heading--filters">
          <div>
            <p className="section-heading__eyebrow">Diretório de eventos</p>
            <h2 id="events-list-heading">
              {loading ? 'Sincronizando eventos' : `${publicEvents.length} ${publicEvents.length === 1 ? 'giveaway público' : 'giveaways públicos'}`}
            </h2>
          </div>

          <div
            className="filter-chips"
            role="group"
            aria-label="Filtrar eventos por status"
            aria-describedby="events-filter-scroll-hint"
            tabIndex={0}
          >
            {FILTERS.map((item) => (
              <button
                key={item.value}
                type="button"
                className={`filter-chip${filter === item.value ? ' active' : ''}`}
                aria-pressed={filter === item.value}
                onClick={() => setFilter(item.value)}
              >
                {item.label} <span>{statusCount(item.value)}</span>
              </button>
            ))}
          </div>
          <p id="events-filter-scroll-hint" className="filter-scroll-hint">
            Deslize ou use Tab para ver todos os filtros <span aria-hidden="true">→</span>
          </p>
        </div>

        {loading && (
          <div className="empty-state" role="status" aria-live="polite">
            <span className="empty-state__signal" aria-hidden="true" />
            <h3>Carregando giveaways</h3>
            <p>Buscando eventos disponíveis no Hub.</p>
          </div>
        )}

        {!loading && error && (
          <div className="empty-state empty-state--error" role="alert">
            <h3>Não foi possível carregar os eventos</h3>
            <p>A conexão com o Hub falhou. Tente novamente em instantes.</p>
            <button type="button" className="btn btn-secondary" onClick={() => void eventsState.reload()}>
              Tentar novamente
            </button>
          </div>
        )}

        {!loading && !error && filteredEvents.length === 0 && (
          <div className="empty-state">
            {filter === 'OPEN' ? <Radio size={30} aria-hidden="true" /> : <Trophy size={30} aria-hidden="true" />}
            <h3>Nenhum evento neste status</h3>
            <p>{filter === 'ALL' ? 'O primeiro giveaway público aparecerá aqui.' : 'Escolha outro filtro para continuar explorando.'}</p>
          </div>
        )}

        {!loading && !error && filteredEvents.length > 0 && (
          <div className="event-grid" aria-live="polite">
            {filteredEvents.map((event) => <EventCard key={event.id} event={event} />)}
          </div>
        )}
      </section>
    </div>
  );
}
