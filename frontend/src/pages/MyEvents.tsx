import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, FilePenLine, Radio, Trophy } from 'lucide-react';
import { getMyEventHistory, getOAuthSession } from '../api/client';
import type { EventHistoryResponse, EventResponse, EventStatus } from '../api/types';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';
import type { Lang, TranslationKey } from '../i18n/translations';

const EMPTY_HISTORY: EventHistoryResponse = { drafts: [], active: [], past: [] };

const STATUS_META: Record<EventStatus, { labelKey: TranslationKey; className: string }> = {
  DRAFT: { labelKey: 'publicStatusDraft', className: 'status-pill--draft' },
  OPEN: { labelKey: 'publicStatusOpen', className: 'status-pill--open' },
  FINALIZING: { labelKey: 'publicStatusFinalizing', className: 'status-pill--finalizing' },
  CLOSED: { labelKey: 'publicStatusClosed', className: 'status-pill--closed' },
  COMPLETED: { labelKey: 'publicStatusCompleted', className: 'status-pill--completed' },
  CANCELLED: { labelKey: 'publicStatusCancelled', className: 'status-pill--cancelled' },
};

const ACTION_LABEL_KEYS: Record<EventStatus, TranslationKey> = {
  DRAFT: 'myEventsActionContinueSetup',
  OPEN: 'myEventsActionManageCapture',
  FINALIZING: 'myEventsActionFollowFinalization',
  CLOSED: 'myEventsActionReviewDraw',
  COMPLETED: 'myEventsActionViewResult',
  CANCELLED: 'myEventsActionReviewEvent',
};

const LIFECYCLE_STEP_KEYS: TranslationKey[] = [
  'myEventsStepConfigure',
  'myEventsStepCapture',
  'myEventsStepFinalize',
  'myEventsStepDraw',
];

function localeFor(lang: Lang) {
  return lang === 'pt-BR' ? 'pt-BR' : 'en-US';
}

function formatNumber(value: number, lang: Lang) {
  return new Intl.NumberFormat(localeFor(lang)).format(value);
}

function lifecycleStep(status: EventStatus) {
  if (status === 'DRAFT') return 0;
  if (status === 'OPEN' || status === 'FINALIZING') return 1;
  if (status === 'CLOSED') return 2;
  return 3;
}

function EventCard({ event }: { event: EventResponse }) {
  const { lang, t } = useI18n();
  const status = STATUS_META[event.status];
  const currentStep = lifecycleStep(event.status);
  const target = event.status === 'COMPLETED'
    ? `/events/${event.id}/result`
    : `/events/${event.id}/manage`;

  return (
    <Link to={target} className={`event-card event-card--manage event-card--${event.status.toLowerCase()}`}>
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
          <span className="event-card__creator">@{event.creatorChannelSlug}</span>
        )}
        <p className="event-card__prize">{event.prize || t('publicPrizePending')}</p>
        <h3 className="event-card__title">{event.title}</h3>
        <p className="event-card__description">
          {event.description || t('publicEventFallbackDescription')}
        </p>
      </div>

      <ol className="lifecycle-mini" aria-label={t('myEventsLifecycleAria', { title: event.title })}>
        {LIFECYCLE_STEP_KEYS.map((stepKey, index) => (
          <li
            key={stepKey}
            className={index < currentStep ? 'is-complete' : index === currentStep ? 'is-current' : undefined}
            aria-current={index === currentStep ? 'step' : undefined}
          >
            <span>{String(index + 1).padStart(2, '0')}</span> {t(stepKey)}
          </li>
        ))}
      </ol>

      <footer className="event-card__footer">
        <span>
          {event.status === 'OPEN' && <><Radio size={15} aria-hidden="true" /> {event.entryCommand || '!participar'}</>}
          {event.status === 'FINALIZING' && <><Radio size={15} aria-hidden="true" /> {t('myEventsFinalSync')}</>}
          {event.status === 'DRAFT' && <><FilePenLine size={15} aria-hidden="true" /> {t('myEventsConfigurationPending')}</>}
          {(event.status === 'CLOSED' || event.status === 'COMPLETED') && (
            <><Trophy size={15} aria-hidden="true" /> {t('myEventsPoolCount', {
              count: formatNumber(event.finalizedParticipantCount, lang),
            })}</>
          )}
          {event.status === 'CANCELLED' && t('myEventsCancelled')}
        </span>
        <span className="event-card__link">
          {t(ACTION_LABEL_KEYS[event.status])} <ArrowRight size={15} aria-hidden="true" />
        </span>
      </footer>
    </Link>
  );
}

interface HistorySectionProps {
  id: string;
  title: string;
  eyebrow: string;
  events: EventResponse[];
  emptyText: string;
}

function HistorySection({ id, title, eyebrow, events, emptyText }: HistorySectionProps) {
  const { lang } = useI18n();

  return (
    <section className="history-section" aria-labelledby={id}>
      <div className="section-heading">
        <div>
          <p className="section-heading__eyebrow">{eyebrow}</p>
          <h2 id={id}>{title}</h2>
        </div>
        <span className="section-heading__count">{formatNumber(events.length, lang)}</span>
      </div>
      {events.length === 0 ? (
        <div className="empty-state empty-state--compact">
          <p>{emptyText}</p>
        </div>
      ) : (
        <div className="event-grid">
          {events.map((event) => <EventCard key={event.id} event={event} />)}
        </div>
      )}
    </section>
  );
}

export default function MyEvents() {
  const { lang, t } = useI18n();
  const fetchHistory = useCallback(async () => {
    const session = await getOAuthSession();
    if (!session.connected) return { history: EMPTY_HISTORY, requiresLogin: true };
    return { history: await getMyEventHistory(), requiresLogin: false };
  }, []);
  const historyState = usePolling(fetchHistory, 10_000);
  const history = historyState.data?.history || EMPTY_HISTORY;
  const loading = historyState.loading && !historyState.data;
  const requiresLogin = historyState.data?.requiresLogin === true;
  const error = Boolean(historyState.error && !historyState.data);

  const totalEvents = useMemo(
    () => history.drafts.length + history.active.length + history.past.length,
    [history],
  );

  return (
    <div className="page hub-page hub-page--my-events">
      <header className="page-hero page-hero--compact">
        <div className="page-hero__copy">
          <p className="page-eyebrow">{t('myEventsEyebrow')}</p>
          <h1 className="page-title">{t('myEventsTitle')}</h1>
          <p className="page-subtitle">{t('myEventsSubtitle')}</p>
        </div>
        <Link to="/events/create" className="btn btn-primary">{t('publicCreateGiveaway')}</Link>
      </header>

      {historyState.error && historyState.data && (
        <div className="notice notice--warning" role="status">{t('myEventsRefreshWarning')}</div>
      )}

      {loading && (
        <div className="empty-state" role="status" aria-live="polite">
          <span className="empty-state__signal" aria-hidden="true" />
          <h2>{t('myEventsLoadingTitle')}</h2>
          <p>{t('myEventsLoadingDescription')}</p>
        </div>
      )}

      {!loading && error && (
        <div className="empty-state empty-state--error" role="alert">
          <h2>{t('myEventsLoadErrorTitle')}</h2>
          <p>{t('myEventsLoadErrorDescription')}</p>
          <div className="empty-state__actions">
            <button type="button" className="btn btn-secondary" onClick={() => void historyState.reload()}>
              {t('publicTryAgain')}
            </button>
            <Link to="/login" className="btn btn-ghost">{t('myEventsReconnect')}</Link>
          </div>
        </div>
      )}

      {!loading && !error && requiresLogin && (
        <div className="empty-state">
          <Radio size={30} aria-hidden="true" />
          <h2>{t('myEventsConnectTitle')}</h2>
          <p>{t('myEventsConnectDescription')}</p>
          <Link to="/login" className="btn btn-primary">{t('myEventsConnectAccount')}</Link>
        </div>
      )}

      {!loading && !error && !requiresLogin && totalEvents === 0 && (
        <div className="empty-state">
          <Trophy size={30} aria-hidden="true" />
          <h2>{t('myEventsFirstTitle')}</h2>
          <p>{t('myEventsFirstDescription')}</p>
          <Link to="/events/create" className="btn btn-primary">{t('myEventsCreateFirst')}</Link>
        </div>
      )}

      {!loading && !error && !requiresLogin && totalEvents > 0 && (
        <div className="history-groups">
          <div className="creator-summary" aria-label={t('myEventsSummaryAria')}>
            <span><strong>{formatNumber(totalEvents, lang)}</strong> {t('myEventsSummaryTotal')}</span>
            <span><strong>{formatNumber(history.active.length, lang)}</strong> {t('myEventsSummaryActive')}</span>
            <span><strong>{formatNumber(history.drafts.length, lang)}</strong> {t('myEventsSummaryDrafts')}</span>
          </div>
          <HistorySection
            id="my-events-drafts"
            eyebrow={t('myEventsDraftsEyebrow')}
            title={t('myEventsDraftsTitle')}
            events={history.drafts}
            emptyText={t('myEventsDraftsEmpty')}
          />
          <HistorySection
            id="my-events-active"
            eyebrow={t('myEventsActiveEyebrow')}
            title={t('myEventsActiveTitle')}
            events={history.active}
            emptyText={t('myEventsActiveEmpty')}
          />
          <HistorySection
            id="my-events-past"
            eyebrow={t('myEventsPastEyebrow')}
            title={t('myEventsPastTitle')}
            events={history.past}
            emptyText={t('myEventsPastEmpty')}
          />
        </div>
      )}
    </div>
  );
}
