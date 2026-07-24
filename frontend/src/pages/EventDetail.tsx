import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  getEvent,
  getEventResult,
  getEventStats,
  type EventLifecycleStats,
  type EventResponse,
  type EventResultResponse,
} from '../api/client';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';
import type { Lang, TranslationKey } from '../i18n/translations';
import { normalizeXPostUrl } from '../utils/giveaway-form';

const STATUS_LABEL_KEYS: Record<EventResponse['status'], TranslationKey> = {
  DRAFT: 'publicStatusDraft',
  OPEN: 'publicStatusOpen',
  FINALIZING: 'publicStatusFinalizing',
  CLOSED: 'publicStatusClosed',
  COMPLETED: 'publicStatusCompleted',
  CANCELLED: 'publicStatusCancelled',
};

const STATUS_CLASSES: Record<EventResponse['status'], string> = {
  DRAFT: 'pill--draft',
  OPEN: 'pill--open',
  FINALIZING: 'pill--finalizing',
  CLOSED: 'pill--closed',
  COMPLETED: 'pill--completed',
  CANCELLED: 'pill--cancelled',
};

const ACTION_TYPE_CONFIG: Record<string, { label: TranslationKey; howTo: TranslationKey }> = {
  chat: { label: 'eventDetailChatEnabled', howTo: 'eventDetailHowToEnterChat' },
  vote: { label: 'eventDetailVoteEnabled', howTo: 'eventDetailHowToEnterVote' },
  sub: { label: 'eventDetailSubEnabled', howTo: 'eventDetailHowToEnterSub' },
  gifted_sub: { label: 'eventDetailGiftedSubEnabled', howTo: 'eventDetailHowToEnterGiftedSub' },
  follow: { label: 'eventDetailFollowEnabled', howTo: 'eventDetailHowToEnterFollow' },
  donation: { label: 'eventDetailDonationEnabled', howTo: 'eventDetailHowToEnterDonation' },
};

type LifecycleKey = 'created' | 'opened' | 'closed' | 'completed';

interface LifecycleStep {
  key: LifecycleKey;
  title: string;
  description: string;
  timestamp: string | null;
}

function localeFor(lang: Lang) {
  return lang === 'pt-BR' ? 'pt-BR' : 'en';
}

function lifecycleState(event: EventResponse, step: LifecycleKey) {
  if (event.status === 'CANCELLED') return step === 'created' ? 'is-complete' : 'is-cancelled';

  const order: LifecycleKey[] = ['created', 'opened', 'closed', 'completed'];
  const currentByStatus: Record<Exclude<EventResponse['status'], 'CANCELLED'>, LifecycleKey> = {
    DRAFT: 'created',
    OPEN: 'opened',
    FINALIZING: 'opened',
    CLOSED: 'closed',
    COMPLETED: 'completed',
  };
  const current = currentByStatus[event.status];
  const stepIndex = order.indexOf(step);
  const currentIndex = order.indexOf(current);
  if (stepIndex < currentIndex) return 'is-complete';
  if (stepIndex === currentIndex) return 'is-current';
  return 'is-pending';
}

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const { lang, t } = useI18n();
  const [result, setResult] = useState<EventResultResponse | null>(null);
  const [resultUnavailable, setResultUnavailable] = useState(false);
  const dateFormatter = useMemo(() => new Intl.DateTimeFormat(localeFor(lang), {
    dateStyle: 'medium',
    timeStyle: 'short',
  }), [lang]);
  const numberFormatter = useMemo(() => new Intl.NumberFormat(localeFor(lang)), [lang]);

  const formatDate = (value: string | null | undefined) => {
    if (!value) return t('eventDetailDatePending');
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? t('eventDetailDateUnavailable') : dateFormatter.format(date);
  };

  const fetchDetail = useCallback(async () => {
    if (!id) throw new Error(t('eventDetailMissingId'));
    const loadedEvent = await getEvent(id);
    try {
      return { event: loadedEvent, stats: await getEventStats(id), statsError: false };
    } catch {
      return { event: loadedEvent, stats: null, statsError: true };
    }
  }, [id, t]);
  const detail = usePolling(fetchDetail, 10_000);
  const currentDetail = detail.data?.event.id === id ? detail.data : null;
  const event = currentDetail?.event || null;
  const stats: EventLifecycleStats | null = currentDetail?.stats || null;
  const statsError = currentDetail?.statsError === true;
  const loading = !currentDetail && !detail.error;
  const error = detail.error ? t('eventDetailUnavailableDescription') : '';

  useEffect(() => {
    let active = true;
    setResultUnavailable(false);
    if (!id || event?.status !== 'COMPLETED') {
      setResult(null);
      return () => { active = false; };
    }

    getEventResult(id)
      .then((loadedResult) => {
        if (active) setResult(loadedResult);
      })
      .catch(() => {
        if (!active) return;
        setResult(null);
        setResultUnavailable(true);
      });

    return () => {
      active = false;
    };
  }, [event?.status, id]);

  useEffect(() => {
    const title = event ? `${event.title} | Blaze Event Hub` : t('metaEventDetailsTitle');
    const description = event
      ? t('eventDetailMetaDescription', { title: event.title })
      : t('metaEventDetailsDescription');
    document.title = title;
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', description);
    document.querySelector<HTMLMetaElement>('meta[property="og:title"]')?.setAttribute('content', title);
    document.querySelector<HTMLMetaElement>('meta[property="og:description"]')?.setAttribute('content', description);
  }, [event, t]);

  const lifecycle = useMemo<LifecycleStep[]>(() => event ? [
    {
      key: 'created',
      title: t('eventDetailLifecycleCreatedTitle'),
      description: t('eventDetailLifecycleCreatedDescription'),
      timestamp: event.createdAt,
    },
    {
      key: 'opened',
      title: t('eventDetailLifecycleOpenedTitle'),
      description: t('eventDetailLifecycleOpenedDescription', { command: event.entryCommand }),
      timestamp: event.openedAt,
    },
    {
      key: 'closed',
      title: t('eventDetailLifecycleClosedTitle'),
      description: t('eventDetailLifecycleClosedDescription'),
      timestamp: event.closedAt,
    },
    {
      key: 'completed',
      title: t('eventDetailLifecycleCompletedTitle'),
      description: t('eventDetailLifecycleCompletedDescription'),
      timestamp: event.completedAt,
    },
  ] : [], [event, t]);

  if (loading) {
    return <div className="hub-page"><div className="empty" role="status">{t('eventDetailLoading')}</div></div>;
  }

  if (!event) {
    return (
      <div className="hub-page">
        <div className="empty-state" role="alert">
          <h1 className="empty-state-title">{t('eventDetailUnavailableTitle')}</h1>
          <p className="empty-state-desc">{error || t('eventDetailUnavailableDescription')}</p>
          <div className="page-actions">
            <button type="button" className="btn btn-secondary" onClick={() => void detail.reload()}>
              {t('publicTryAgain')}
            </button>
            <Link className="btn btn-ghost" to="/events">{t('eventDetailViewGiveaways')}</Link>
          </div>
        </div>
      </div>
    );
  }

  const participantCount = stats
    ? (event.status === 'DRAFT' || event.status === 'OPEN' || event.status === 'FINALIZING'
      ? stats.participantCount
      : stats.finalizedParticipantCount)
    : event.status === 'CLOSED' || event.status === 'COMPLETED'
      ? event.finalizedParticipantCount
      : null;
  const participantLabel = participantCount === null
    ? t('eventDetailMetricUnavailable')
    : t(participantCount === 1 ? 'eventDetailParticipantOne' : 'eventDetailParticipantMany', {
        count: numberFormatter.format(participantCount),
      });
  const safeXPostUrl = event.xPostUrl ? normalizeXPostUrl(event.xPostUrl) : null;

  let captureDescriptionKey: TranslationKey;
  if (event.status === 'OPEN') {
    captureDescriptionKey = stats?.captureHealth === 'HEALTHY'
      ? 'eventDetailCaptureHealthy'
      : stats?.captureHealth === 'DEGRADED'
        ? 'eventDetailCaptureDegraded'
        : 'eventDetailCaptureWaiting';
  } else if (event.status === 'FINALIZING') {
    captureDescriptionKey = 'eventDetailCaptureFinalizing';
  } else if (event.status === 'DRAFT') {
    captureDescriptionKey = 'eventDetailCaptureDraft';
  } else {
    captureDescriptionKey = 'eventDetailCaptureClosed';
  }

  return (
    <div className="hub-page event-detail-page">
      <header className="page-hero">
        <div className="page-hero-copy">
          <span className="page-eyebrow">{t('eventDetailEyebrow')}</span>
          <h1 className="page-title">{event.title}</h1>
          {event.description && <p className="page-subtitle">{event.description}</p>}
        </div>
        <div className="page-actions">
          {event.creatorChannelSlug && event.status === 'OPEN' && (
            <a
              className="btn btn-primary"
              href={`https://blaze.stream/${encodeURIComponent(event.creatorChannelSlug)}`}
              target="_blank"
              rel="noreferrer"
            >
              {t('eventDetailOpenStream')} <span aria-hidden="true">↗</span>
            </a>
          )}
          {event.status === 'COMPLETED' && (
            <Link className="btn btn-primary" to={`/events/${event.id}/result`}>
              {t('eventDetailViewDrawRecord')}
            </Link>
          )}
          <span className={`pill ${STATUS_CLASSES[event.status]}`}>{t(STATUS_LABEL_KEYS[event.status])}</span>
        </div>
      </header>

      {error && <div className="notice notice--warning" role="status">{t('eventDetailRefreshWarning')}</div>}

      {event.status === 'CANCELLED' && (
        <div className="notice notice-danger" role="status">{t('eventDetailCancelledWarning')}</div>
      )}

      {event.status === 'FINALIZING' && (
        <div className="notice notice--warning" role="status">{t('eventDetailFinalizingWarning')}</div>
      )}

      {statsError && (
        <div className="notice notice-danger" role="status">
          <strong>{t('eventDetailStatsUnavailable')}</strong>. {t('eventDetailStatsWarning')}
        </div>
      )}

      <div className="event-detail-grid">
        <section className="control-card prize-card" aria-labelledby="prize-title">
          <span className="section-label">{t('eventDetailPrizeLabel')}</span>
          <h2 id="prize-title">{event.prize}</h2>
          {event.creatorChannelSlug && (
            <p className="channel-identity">
              {t('eventDetailCreatorBy')} <strong>{event.creatorChannelDisplayName || event.creatorChannelSlug}</strong>{' '}
              <a href={`https://blaze.stream/${encodeURIComponent(event.creatorChannelSlug)}`} target="_blank" rel="noreferrer">
                @{event.creatorChannelSlug} <span aria-hidden="true">↗</span>
              </a>
            </p>
          )}
          <p>{t('eventDetailPrizeResponsibility')}</p>
          {safeXPostUrl && (
            <a
              className="btn btn-secondary"
              href={safeXPostUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('eventDetailViewXPost')} <span aria-hidden="true">↗</span>
            </a>
          )}
        </section>

        <section className="control-card command-card" aria-labelledby="command-title">
          {event.enabledActionTypes && event.enabledActionTypes.length > 1 && (
            <div className="action-types-chips">
              <span className="section-label">{t('eventDetailActionTypesLabel')}</span>
              <div className="chip-row">
                {event.enabledActionTypes.map((type) => {
                  const cfg = ACTION_TYPE_CONFIG[type];
                  return (
                    <span key={type} className="pill pill--action-type">
                      {cfg ? t(cfg.label) : type}
                    </span>
                  );
                })}
              </div>
            </div>
          )}
          <span className="section-label">{event.status === 'OPEN' ? t('eventDetailEntryCommandActive' as any) : t('eventDetailEntryCommand' as any)}</span>
          <h2 id="command-title">{event.status === 'OPEN' ? t('eventDetailEnterNowLabel' as any) : t('eventDetailCommandLabel' as any)}</h2>
          {event.enabledActionTypes && event.enabledActionTypes.includes('chat') && (
            <code className={`signal-command${event.status === 'OPEN' ? ' is-active' : ''}`}>
              {event.entryCommand}
            </code>
          )}
          <div className="action-type-instructions">
            {event.enabledActionTypes && event.enabledActionTypes.map((type) => {
              const cfg = ACTION_TYPE_CONFIG[type];
              return cfg ? <p key={type}>{t(cfg.howTo)}</p> : null;
            })}
          </div>
          {event.status === 'OPEN' ? (
            <p>{t('eventDetailCaptureOpen' as any)}</p>
          ) : (
            <p>{t(captureDescriptionKey)}</p>
          )}
        </section>
      </div>

      <section className="event-vitals" aria-label={t('eventDetailSummaryAria')}>
        <div className="event-vital">
          <span className="event-vital__index" aria-hidden="true">01</span>
          <span className="event-vital__label">{t(event.status === 'OPEN' || event.status === 'FINALIZING' ? 'eventDetailCurrentPool' : 'eventDetailFinalPool')}</span>
          <strong>{participantLabel}</strong>
        </div>
        <div className="event-vital">
          <span className="event-vital__index" aria-hidden="true">02</span>
          <span className="event-vital__label">{t('eventDetailChancePerUser')}</span>
          <strong>{numberFormatter.format(1)}</strong>
        </div>
        <div className="event-vital">
          <span className="event-vital__index" aria-hidden="true">03</span>
          <span className="event-vital__label">{t('eventDetailPoolState')}</span>
          <strong>
            {t(event.status === 'OPEN'
              ? 'eventDetailPoolOpen'
              : event.status === 'FINALIZING'
                ? 'eventDetailPoolClosing'
                : 'eventDetailPoolLocked')}
          </strong>
        </div>
      </section>

      <section className="control-card lifecycle-card" aria-labelledby="lifecycle-title">
        <div className="section-heading">
          <div>
            <span className="section-label">{t('eventDetailTimelineLabel')}</span>
            <h2 id="lifecycle-title">{t('eventDetailLifecycleTitle')}</h2>
          </div>
        </div>
        <ol className="lifecycle">
          {lifecycle.map((step) => (
            <li key={step.key} className={`lifecycle-step ${lifecycleState(event, step.key)}`}>
              <span className="lifecycle-marker" aria-hidden="true" />
              <div className="lifecycle-copy">
                <strong>{step.title}</strong>
                <p>{step.description}</p>
                <time dateTime={step.timestamp || undefined}>{formatDate(step.timestamp)}</time>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="control-card event-times" aria-labelledby="times-title">
        <span className="section-label">{t('eventDetailTimesLabel')}</span>
        <h2 id="times-title">{t('eventDetailTimesTitle')}</h2>
        <dl className="proof-grid">
          <div><dt>{t('eventDetailScheduledStart')}</dt><dd>{formatDate(event.startsAt)}</dd></div>
          <div><dt>{t('eventDetailScheduledEnd')}</dt><dd>{formatDate(event.endsAt)}</dd></div>
          <div><dt>{t('eventDetailCaptureStarted')}</dt><dd>{formatDate(event.openedAt)}</dd></div>
          <div><dt>{t('eventDetailEntryCutoff')}</dt><dd>{formatDate(event.finalizationCutoffAt)}</dd></div>
          <div><dt>{t('eventDetailPoolFinalized')}</dt><dd>{formatDate(event.closedAt)}</dd></div>
        </dl>
      </section>

      {resultUnavailable && (
        <div className="notice notice-danger" role="status">{t('eventDetailResultUnavailable')}</div>
      )}

      {result && (
        <section className="winner-card" aria-labelledby="winner-title">
          <span className="section-label">{t('eventDetailOfficialResult')}</span>
          <div className="winner-avatar" aria-hidden="true">
            {(result.winnerDisplayName || result.winnerUsername || '?').slice(0, 1).toUpperCase()}
          </div>
          <h2 id="winner-title" className="winner-name">
            {result.winnerDisplayName || result.winnerUsername}
          </h2>
          <p className="winner-meta">
            {result.winnerUsername ? `@${result.winnerUsername} · ` : ''}
            {t('eventDetailDrawnOn', { date: formatDate(result.selectedAt) })}
          </p>
          <Link className="btn btn-secondary" to={`/events/${event.id}/result`}>
            {t('eventDetailViewDrawRecord')}
          </Link>
        </section>
      )}
    </div>
  );
}
