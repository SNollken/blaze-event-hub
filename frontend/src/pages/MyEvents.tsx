import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyEventHistory, getOAuthSession } from '../api/client';
import type { EventHistoryResponse, EventResponse } from '../api/client';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

const STATUS_MAP: Record<string, { cls: string; labelKey: TranslationKey }> = {
  OPEN: { cls: 'pill--open', labelKey: 'statusOpen' },
  CLOSED: { cls: 'pill--closed', labelKey: 'statusClosed' },
  DRAWING: { cls: 'pill--completed', labelKey: 'statusDrawing' },
  COMPLETED: { cls: 'pill--completed', labelKey: 'statusCompleted' },
  DRAFT: { cls: 'pill--draft', labelKey: 'statusDraft' },
  CANCELLED: { cls: 'pill--cancelled', labelKey: 'statusCancelled' },
};

const emptyHistory: EventHistoryResponse = {
  drafts: [],
  upcoming: [],
  past: [],
};

function EventCard({ event }: { event: EventResponse }) {
  const { t } = useI18n();
  const status = STATUS_MAP[event.status] || STATUS_MAP.DRAFT;

  return (
    <Link
      to={`/events/${event.id}`}
      className="card"
      style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '18px 20px', minHeight: 140 }}
    >
      <span className={`pill ${status.cls}`}>{t(status.labelKey)}</span>
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
        {event.description?.slice(0, 80) || t('noDescription')}
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
        <span>{t('rulesParticipantsMeta', { rules: event.rules?.length || 0, participants: event.participantCount || 0 })}</span>
        <span style={{ color: 'var(--accent-light)' }}>{t('view')}</span>
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
  const { t } = useI18n();
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
          setError(err instanceof Error ? err.message : t('myEventsLoadError'));
        }
      } finally {
        if (alive) setLoading(false);
      }
    }

    void loadHistory();

    return () => {
      alive = false;
    };
  }, [t]);

  const totalEvents = useMemo(
    () => history.drafts.length + history.upcoming.length + history.past.length,
    [history],
  );

  if (loading) return <div className="empty">{t('myEventsLoading')}</div>;

  return (
    <div style={{ padding: '32px 40px', maxWidth: 960 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28, gap: 16 }}>
        <div>
          <h1 className="page-title">{t('myEventsTitle')}</h1>
          <p className="page-subtitle" style={{ marginBottom: 0 }}>
            {t(totalEvents === 1 ? 'eventCreated' : 'eventsCreated', { count: totalEvents })}
          </p>
        </div>
        <Link to="/events/create" className="btn btn-primary">{t('quickCreate')}</Link>
      </div>

      {error && (
        <div className="empty" style={{ color: 'var(--danger)' }}>
          {error}
          <div style={{ marginTop: 12 }}>
            <Link to="/login" className="btn btn-secondary">{t('connectBlaze')}</Link>
          </div>
        </div>
      )}

      {!error && requiresLogin && (
        <div className="empty">
          {t('connectToViewEvents')}
          <div style={{ marginTop: 12 }}>
            <Link to="/login" className="btn btn-secondary">{t('connectBlaze')}</Link>
          </div>
        </div>
      )}

      {!error && !requiresLogin && totalEvents === 0 && (
        <div className="empty">
          {t('noEventsCreated')}
          <div style={{ marginTop: 12 }}>
            <Link to="/events/create" style={{ color: 'var(--accent-light)', fontSize: 13 }}>{t('createFirstEvent')}</Link>
          </div>
        </div>
      )}

      {!error && !requiresLogin && totalEvents > 0 && (
        <>
          <HistorySection title={t('drafts')} events={history.drafts} emptyText={t('noDrafts')} />
          <HistorySection title={t('upcoming')} events={history.upcoming} emptyText={t('noUpcoming')} />
          <HistorySection title={t('past')} events={history.past} emptyText={t('noPast')} />
        </>
      )}
    </div>
  );
}
