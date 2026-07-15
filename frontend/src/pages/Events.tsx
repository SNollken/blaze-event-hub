import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, Radio, Trophy } from 'lucide-react';
import { getEvents } from '../api/client';
import type { EventResponse, EventStatus } from '../api/types';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';
import type { Lang, TranslationKey } from '../i18n/translations';

type PublicFilter = 'ALL' | Extract<EventStatus, 'OPEN' | 'CLOSED' | 'COMPLETED'>;

const STATUS_META: Record<EventStatus, { labelKey: TranslationKey; className: string }> = {
  DRAFT: { labelKey: 'publicStatusDraft', className: 'status-pill--draft' },
  OPEN: { labelKey: 'publicStatusOpen', className: 'status-pill--open' },
  FINALIZING: { labelKey: 'publicStatusFinalizing', className: 'status-pill--finalizing' },
  CLOSED: { labelKey: 'publicStatusClosed', className: 'status-pill--closed' },
  COMPLETED: { labelKey: 'publicStatusCompleted', className: 'status-pill--completed' },
  CANCELLED: { labelKey: 'publicStatusCancelled', className: 'status-pill--cancelled' },
};

const FILTERS: { value: PublicFilter; labelKey: TranslationKey }[] = [
  { value: 'ALL', labelKey: 'eventsFilterAll' },
  { value: 'OPEN', labelKey: 'eventsFilterOpen' },
  { value: 'CLOSED', labelKey: 'eventsFilterClosed' },
  { value: 'COMPLETED', labelKey: 'eventsFilterCompleted' },
];

function localeFor(lang: Lang) {
  return lang === 'pt-BR' ? 'pt-BR' : 'en-US';
}

function formatDate(value: string | null, lang: Lang) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(localeFor(lang), {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function formatNumber(value: number, lang: Lang) {
  return new Intl.NumberFormat(localeFor(lang)).format(value);
}

function EventCard({ event }: { event: EventResponse }) {
  const { lang, t } = useI18n();
  const status = STATUS_META[event.status];
  const endsAt = formatDate(event.endsAt, lang);
  const participantCount = formatNumber(event.finalizedParticipantCount, lang);

  return (
    <Link to={`/events/${event.id}`} className={`event-card event-card--${event.status.toLowerCase()}`}>
      <div className="event-card__topline">
        <span className={`status-pill ${status.className}`}>{t(status.labelKey)}</span>
        {event.status === 'OPEN' && (
          <span className="event-card__signal" aria-label={t('publicCaptureActiveAria')}>
            <span aria-hidden="true" /> {t('publicLive')}
          </span>
        )}
      </div>

      <div className="event-card__body">
        {event.creatorChannelSlug && (
          <span className="event-card__creator">
            {event.creatorChannelDisplayName || event.creatorChannelSlug} · @{event.creatorChannelSlug}
          </span>
        )}
        <p className="event-card__prize">{event.prize || t('publicPrizePending')}</p>
        <h2 className="event-card__title">{event.title}</h2>
        <p className="event-card__description">
          {event.description || t('publicEventFallbackDescription')}
        </p>
      </div>

      {event.status === 'OPEN' ? (
        <div className="event-card__command">
          <span>{t('publicChatCommand')}</span>
          <code>{event.entryCommand || '!participar'}</code>
        </div>
      ) : event.status === 'FINALIZING' ? (
        <div className="event-card__meta">
          <Radio size={15} aria-hidden="true" />
          <span>{t('publicFinalSync')}</span>
        </div>
      ) : (
        <div className="event-card__meta">
          <Trophy size={15} aria-hidden="true" />
          <span>
            {t(
              event.finalizedParticipantCount === 1
                ? 'publicFinalPoolCountOne'
                : 'publicFinalPoolCountMany',
              { count: participantCount },
            )}
          </span>
        </div>
      )}

      <footer className="event-card__footer">
        <span>
          {endsAt
            ? <><CalendarDays size={15} aria-hidden="true" /> {endsAt}</>
            : t('publicNoEndTime')}
        </span>
        <span className="event-card__link">
          {t('publicDetails')} <ArrowRight size={15} aria-hidden="true" />
        </span>
      </footer>
    </Link>
  );
}

export default function Events() {
  const { lang, t } = useI18n();
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
  const publicCount = formatNumber(publicEvents.length, lang);

  return (
    <div className="page hub-page hub-page--events">
      <header className="page-hero page-hero--compact">
        <div className="page-hero__copy">
          <p className="page-eyebrow">{t('eventsEyebrow')}</p>
          <h1 className="page-title">{t('eventsTitle')}</h1>
          <p className="page-subtitle">{t('eventsSubtitle')}</p>
        </div>
        <Link to="/events/create" className="btn btn-primary">{t('publicCreateGiveaway')}</Link>
      </header>

      {eventsState.error && eventsState.data && (
        <div className="notice notice--warning" role="status">{t('eventsRefreshWarning')}</div>
      )}

      <section className="hub-section" aria-labelledby="events-list-heading">
        <div className="section-heading section-heading--filters">
          <div>
            <p className="section-heading__eyebrow">{t('eventsDirectoryEyebrow')}</p>
            <h2 id="events-list-heading">
              {loading
                ? t('eventsSyncing')
                : t(publicEvents.length === 1 ? 'eventsPublicCountOne' : 'eventsPublicCountMany', {
                    count: publicCount,
                  })}
            </h2>
          </div>

          <div
            className="filter-chips"
            role="group"
            aria-label={t('eventsFilterAria')}
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
                {t(item.labelKey)} <span>{formatNumber(statusCount(item.value), lang)}</span>
              </button>
            ))}
          </div>
          <p id="events-filter-scroll-hint" className="filter-scroll-hint">
            {t('eventsFilterHint')} <span aria-hidden="true">→</span>
          </p>
        </div>

        {loading && (
          <div className="empty-state" role="status" aria-live="polite">
            <span className="empty-state__signal" aria-hidden="true" />
            <h3>{t('eventsLoadingTitle')}</h3>
            <p>{t('eventsLoadingDescription')}</p>
          </div>
        )}

        {!loading && error && (
          <div className="empty-state empty-state--error" role="alert">
            <h3>{t('eventsLoadErrorTitle')}</h3>
            <p>{t('eventsLoadErrorDescription')}</p>
            <button type="button" className="btn btn-secondary" onClick={() => void eventsState.reload()}>
              {t('publicTryAgain')}
            </button>
          </div>
        )}

        {!loading && !error && filteredEvents.length === 0 && (
          <div className="empty-state">
            {filter === 'OPEN' ? <Radio size={30} aria-hidden="true" /> : <Trophy size={30} aria-hidden="true" />}
            <h3>{t('eventsEmptyTitle')}</h3>
            <p>{t(filter === 'ALL' ? 'eventsEmptyAll' : 'eventsEmptyFiltered')}</p>
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
