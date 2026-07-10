import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getEvents } from '../api/client';
import type { EventResponse } from '../api/client';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

const STATUS_MAP: Record<string, { pill: string; labelKey: TranslationKey }> = {
  OPEN:      { pill: 'pill--open',      labelKey: 'statusOpen' },
  CLOSED:    { pill: 'pill--closed',    labelKey: 'statusClosed' },
  DRAWING:   { pill: 'pill--completed', labelKey: 'statusDrawing' },
  COMPLETED: { pill: 'pill--completed', labelKey: 'statusCompleted' },
  CANCELLED: { pill: 'pill--cancelled', labelKey: 'statusCancelled' },
  DRAFT:     { pill: 'pill--draft',     labelKey: 'statusDraft' },
};

export default function Events() {
  const { t } = useI18n();
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<TranslationKey | null>(null);

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
          setError('eventsLoadError');
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
    ? t('eventsLoading')
    : error
      ? t('eventsLoadFailed')
      : events.length === 1
        ? t('eventFound', { count: events.length })
        : t('eventsFoundCount', { count: events.length });

  return (
    <div style={{ padding: '32px 40px' }}>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
        marginBottom: 28,
      }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 600, color: 'var(--fg)', letterSpacing: '-0.3px', margin: 0 }}>
            {t('eventsTitle')}
          </h1>
          <p style={{ fontSize: 13, color: 'var(--muted)', marginTop: 2 }}>
            {summary}
          </p>
        </div>
        <Link to="/events/create" className="btn btn-primary">
          {t('createBtn')}
        </Link>
      </div>

      {loading && (
        <div className="empty">{t('eventsLoading')}</div>
      )}

      {!loading && error && (
        <div className="empty" style={{ color: 'var(--danger)' }}>
          {t(error)}
        </div>
      )}

      {!loading && !error && events.length === 0 ? (
        <div className="empty">
          {t('noEventsYet')}
        </div>
      ) : null}

      {!loading && !error && events.length > 0 && (
        <>
          {openEvents.length > 0 && (
            <Section
              label={t('filterOpen')}
              count={openEvents.length}
              events={openEvents}
            />
          )}

          {closedEvents.length > 0 && (
            <Section
              label={t('closedEvents')}
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
  const { t } = useI18n();
  const status = STATUS_MAP[event.status] || STATUS_MAP.DRAFT;
  const rulesCount = event.rules?.length ?? 0;

  return (
    <Link
      to={`/events/${event.id}`}
      className="card"
      style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '14px 16px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span className={`pill ${status.pill}`}>{t(status.labelKey)}</span>
      </div>

      <div className="card-title" style={{ fontSize: 14 }}>
        {event.title}
      </div>

      <div className="card-desc" style={{ whiteSpace: 'normal', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
        {event.description?.slice(0, 120) || t('noDescription')}
      </div>

      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginTop: 'auto', paddingTop: 6,
        borderTop: '1px solid var(--border-card)',
        fontSize: 12, color: 'var(--muted)',
      }}>
        <span>
          {rulesCount === 0
            ? t('noRules')
            : rulesCount === 1
              ? t('ruleCountOne', { count: rulesCount })
              : t('ruleCount', { count: rulesCount })}
        </span>
        <span style={{ color: 'var(--accent-light)', fontWeight: 510 }}>
          {t('view')}
        </span>
      </div>
    </Link>
  );
}
