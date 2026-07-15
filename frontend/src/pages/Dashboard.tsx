import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Radio, Trophy, Users } from 'lucide-react';
import { getEventStats, getEvents, getOAuthSession } from '../api/client';
import type { EventLifecycleStats, EventResponse, OAuthSessionResponse } from '../api/client';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';
import type { Lang, TranslationKey } from '../i18n/translations';
import { defaultEntryCommand } from '../utils/giveaway-form';

function commandLabel(command: string, lang: Lang) {
  return command.trim() || defaultEntryCommand(lang);
}

type Translate = (key: TranslationKey, params?: Record<string, string | number>) => string;

function captureHealthLabel(stats: EventLifecycleStats | null, t: Translate) {
  if (!stats) return t('dashboardCaptureAwaitingData');
  if (stats.captureHealth === 'HEALTHY') return t('dashboardCaptureHealthy');
  if (stats.captureHealth === 'STARTING') return t('dashboardCaptureStarting');
  if (stats.captureHealth === 'DEGRADED') return t('dashboardCaptureAttention');
  if (stats.captureHealth === 'FINALIZING') return t('dashboardCaptureFinalizing');
  return t('dashboardCaptureInactive');
}

function EventCard({ event }: { event: EventResponse }) {
  const { lang, t } = useI18n();

  return (
    <Link to={`/events/${event.id}`} className="event-card event-card--live">
      <div className="event-card__topline">
        <span className="status-pill status-pill--open">{t('dashboardCapturingNow')}</span>
        <span className="event-card__signal" aria-label={t('dashboardOpenEntriesAria')}>
          <span aria-hidden="true" /> {t('dashboardOpenShort')}
        </span>
      </div>
      <div className="event-card__body">
        {event.creatorChannelSlug && (
          <span className="event-card__creator">
            {event.creatorChannelDisplayName || event.creatorChannelSlug} · @{event.creatorChannelSlug}
          </span>
        )}
        <p className="event-card__prize">{event.prize || t('dashboardPrizePending')}</p>
        <h3 className="event-card__title">{event.title}</h3>
        <p className="event-card__description">
          {event.description || t('dashboardEventFallbackDescription')}
        </p>
      </div>
      <div className="event-card__command">
        <span>{t('dashboardEntryCommand')}</span>
        <code>{commandLabel(event.entryCommand, lang)}</code>
      </div>
      <span className="event-card__link">
        {t('dashboardViewGiveaway')} <ArrowRight size={16} aria-hidden="true" />
      </span>
    </Link>
  );
}

export default function Dashboard() {
  const { lang, t } = useI18n();
  const numberFormatter = useMemo(() => new Intl.NumberFormat(lang), [lang]);
  const fetchEvents = useCallback(() => getEvents('OPEN'), []);
  const fetchOAuth = useCallback(() => getOAuthSession(), []);
  const eventsState = usePolling(fetchEvents, 10_000);
  const oauthState = usePolling(fetchOAuth, 60_000);
  const events = eventsState.data || [];
  const featuredEvent = events[0] ?? null;
  const featuredEventId = featuredEvent?.id || null;
  const fetchFeaturedStats = useCallback(
    () => featuredEventId ? getEventStats(featuredEventId) : Promise.resolve<EventLifecycleStats | null>(null),
    [featuredEventId],
  );
  const featuredStatsState = usePolling(fetchFeaturedStats, 10_000);
  const featuredStats = featuredStatsState.data?.eventId === featuredEventId
    ? featuredStatsState.data
    : null;
  const moreEvents = useMemo(() => events.slice(1, 7), [events]);
  const oauth: OAuthSessionResponse | null = oauthState.data;
  const connected = oauth?.connected === true;
  const loading = eventsState.loading && !eventsState.data;
  const eventsError = Boolean(eventsState.error && !eventsState.data);
  const statsError = Boolean(featuredStatsState.error);

  return (
    <div className="page hub-page hub-page--dashboard">
      <header className="page-hero page-hero--split">
        <div className="page-hero__copy">
          <p className="page-eyebrow">{t('dashboardHeroEyebrow')}</p>
          <h1 className="page-title">{t('dashTitle')}</h1>
          <p className="page-subtitle">{t('dashboardHeroSubtitle')}</p>
          <div className="page-hero__actions">
            <Link to="/events" className="btn btn-primary">
              {t('dashboardExplore')} <ArrowRight size={17} aria-hidden="true" />
            </Link>
            <Link to={connected ? '/events/create' : '/login'} className="btn btn-secondary">
              {connected ? t('dashboardCreate') : t('dashboardConnectCreator')}
            </Link>
          </div>
        </div>
      </header>

      {eventsState.error && eventsState.data && (
        <div className="notice notice--warning" role="status">
          {t('dashboardRefreshWarning')}
        </div>
      )}

      <section className="hub-section" aria-labelledby="open-events-heading">
        <div className="section-heading">
          <div>
            <p className="section-heading__eyebrow">{t('dashboardOpenSignal')}</p>
            <h2 id="open-events-heading">{t('sectionOpen')}</h2>
          </div>
          {!loading && !eventsError && (
            <span className="section-heading__count">
              {t(events.length === 1 ? 'dashboardEventCountOne' : 'dashboardEventCountMany', {
                count: numberFormatter.format(events.length),
              })}
            </span>
          )}
        </div>

        {loading && (
          <div className="empty-state" role="status" aria-live="polite">
            <span className="empty-state__signal" aria-hidden="true" />
            <h3>{t('dashboardLoadingTitle')}</h3>
            <p>{t('dashboardLoadingDescription')}</p>
          </div>
        )}

        {!loading && eventsError && (
          <div className="empty-state empty-state--error" role="alert">
            <h3>{t('dashboardLoadErrorTitle')}</h3>
            <p>{t('dashboardLoadErrorDescription')}</p>
            <button type="button" className="btn btn-secondary" onClick={() => void eventsState.reload()}>
              {t('dashboardRetry')}
            </button>
          </div>
        )}

        {!loading && !eventsError && !featuredEvent && (
          <div className="empty-state">
            <Trophy size={30} aria-hidden="true" />
            <h3>{t('dashboardEmptyTitle')}</h3>
            <p>{t('dashboardEmptyDescription')}</p>
            <Link to="/events" className="btn btn-secondary">{t('dashboardViewClosed')}</Link>
          </div>
        )}

        {!loading && !eventsError && featuredEvent && (
          <>
            <article className="featured-event">
              <div className="featured-event__content">
                <div className="featured-event__topline">
                  <span className="status-pill status-pill--open">{t('dashboardCapturingNow')}</span>
                  <span className="featured-event__live"><span aria-hidden="true" /> {t('dashboardOpenEntriesAria')}</span>
                </div>
                <p className="featured-event__prize">{featuredEvent.prize || t('dashboardPrizePending')}</p>
                <h3>{featuredEvent.title}</h3>
                <p>{featuredEvent.description || t('dashboardFeaturedFallbackDescription')}</p>
                <div className="featured-event__command">
                  <span>{t('dashboardTypeInChat')}</span>
                  <code>{commandLabel(featuredEvent.entryCommand, lang)}</code>
                </div>
                <Link to={`/events/${featuredEvent.id}`} className="btn btn-primary">
                  {t('dashboardOpenEvent')} <ArrowRight size={17} aria-hidden="true" />
                </Link>
              </div>

              <div className="featured-event__telemetry" aria-label={t('dashboardFeaturedStateAria')}>
                <div className="telemetry-item">
                  <Users size={18} aria-hidden="true" />
                  <strong>
                    {featuredStats ? numberFormatter.format(featuredStats.participantCount) : '—'}
                  </strong>
                  <span>{t('dashboardParticipantsCaptured')}</span>
                </div>
                <div className="telemetry-item">
                  <Radio size={18} aria-hidden="true" />
                  <strong>{captureHealthLabel(featuredStats, t)}</strong>
                  <span>{t('dashboardCaptureHealth')}</span>
                </div>
                {statsError && <p className="telemetry-note" role="status">{t('dashboardMetricsUnavailable')}</p>}
                <ol className="lifecycle-mini lifecycle-mini--vertical">
                  <li className="is-complete"><span>01</span> {t('dashboardLifecycleConfigured')}</li>
                  <li className="is-current"><span>02</span> {t('dashboardLifecycleCapturing')}</li>
                  <li><span>03</span> {t('dashboardLifecycleFinalize')}</li>
                  <li><span>04</span> {t('dashboardLifecycleDraw')}</li>
                </ol>
              </div>
            </article>

            {moreEvents.length > 0 && (
              <div className="event-grid event-grid--compact">
                {moreEvents.map((event) => <EventCard key={event.id} event={event} />)}
              </div>
            )}
          </>
        )}
      </section>

      <section className="creator-cta" aria-labelledby="creator-cta-heading">
        <div>
          <p className="page-eyebrow">{t('dashboardCreatorEyebrow')}</p>
          <h2 id="creator-cta-heading">{t('dashboardCreatorTitle')}</h2>
          <p>{t('dashboardCreatorDescription')}</p>
        </div>
        <Link to={connected ? '/events/create' : '/login'} className="btn btn-secondary">
          {connected ? t('dashboardConfigureNew') : t('dashboardConnectBlaze')}
        </Link>
      </section>
    </div>
  );
}
