import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ApiError,
  addManualParticipant,
  executeDraw,
  getEvent,
  getEventParticipants,
  getEventResult,
  getEventStats,
  getMe,
  getOAuthSession,
  type EventLifecycleStats,
  type EventParticipantResponse,
  type EventResponse,
  type EventResultResponse,
  type MemberProfile,
} from '../api/client';
import { Modal } from '../components/Modal';
import { addToast } from '../components/Toast';
import { getUserFacingErrorMessage, UserFacingError } from '../errors/user-facing-error';
import { useI18n } from '../i18n/I18nContext';

type DrawPhase = 'idle' | 'rolling' | 'done';

const ROLL_INTERVAL_MS = 80;
const MINIMUM_ROLL_MS = 1400;

function formatDate(value: string, locale: string, unavailable: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? unavailable
    : new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function participantName(participant: EventParticipantResponse) {
  return participant.displayName || participant.blazeUsername || participant.blazeUserId;
}

function getErrorMessage(error: unknown, fallback: string, creatorOnly: string) {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return creatorOnly;
  }
  return getUserFacingErrorMessage(error, fallback);
}

function wait(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function prefersReducedMotion() {
  return typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export default function LiveDraw() {
  const { id } = useParams<{ id: string }>();
  const { lang, t } = useI18n();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [stats, setStats] = useState<EventLifecycleStats | null>(null);
  const [participants, setParticipants] = useState<EventParticipantResponse[]>([]);
  const [creator, setCreator] = useState<MemberProfile | null>(null);
  const [result, setResult] = useState<EventResultResponse | null>(null);
  const [phase, setPhase] = useState<DrawPhase>('idle');
  const [displayName, setDisplayName] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [manualDonationOpen, setManualDonationOpen] = useState(false);
  const [manualUsername, setManualUsername] = useState('');
  const [manualAmount, setManualAmount] = useState(1);
  const [manualActionType, setManualActionType] = useState('donation');
  const [manualSubmitting, setManualSubmitting] = useState(false);
  const spinRef = useRef<ReturnType<typeof window.setInterval> | null>(null);
  const mountedRef = useRef(true);

  const stopRolling = useCallback(() => {
    if (spinRef.current !== null) {
      window.clearInterval(spinRef.current);
      spinRef.current = null;
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      stopRolling();
    };
  }, [stopRolling]);

  useEffect(() => {
    let active = true;
    const abortController = new AbortController();

    async function loadDraw(eventId: string) {
      setLoading(true);
      setError('');
      setResult(null);
      setPhase('idle');

      try {
        const session = await getOAuthSession(abortController.signal);
        if (!session.connected) {
          throw new UserFacingError(t('drawConnectBlaze'));
        }

        const [loadedCreator, loadedEvent, loadedStats, loadedParticipants] = await Promise.all([
          getMe(abortController.signal),
          getEvent(eventId, abortController.signal),
          getEventStats(eventId, abortController.signal),
          getEventParticipants(eventId, abortController.signal),
        ]);
        if (!active) return;

        setCreator(loadedCreator);
        setEvent(loadedEvent);
        setStats(loadedStats);
        setParticipants(loadedParticipants);

        if (loadedEvent.status === 'COMPLETED') {
          const existingResult = await getEventResult(eventId, abortController.signal);
          if (!active) return;
          setResult(existingResult);
          setDisplayName(existingResult.winnerDisplayName || existingResult.winnerUsername || t('drawWinnerFallback'));
          setPhase('done');
        }
      } catch (loadError) {
        if (active && loadError instanceof DOMException && loadError.name === 'AbortError') return;
        if (active) {
          setError(getErrorMessage(loadError, t('drawPrepareFallback'), t('drawCreatorOnly')));
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    if (id) {
      void loadDraw(id);
    } else {
      setError(t('drawMissingId'));
      setLoading(false);
    }

    return () => {
      active = false;
      abortController.abort();
    };
  }, [id, t]);

  const numberFormatter = useMemo(() => new Intl.NumberFormat(lang), [lang]);

  const sortedParticipants = useMemo(
    () => [...participants].sort((first, second) => (
      participantName(first).localeCompare(participantName(second), lang, { sensitivity: 'base' })
    )),
    [lang, participants],
  );

  const expectedPoolCount = stats?.finalizedParticipantCount
    ?? event?.finalizedParticipantCount
    ?? 0;
  const poolHasParticipants = expectedPoolCount > 0 && participants.length > 0;
  const poolMatchesSnapshot = expectedPoolCount === participants.length;
  const canDraw = Boolean(id)
    && event?.status === 'CLOSED'
    && stats?.canDraw !== false
    && poolHasParticipants
    && poolMatchesSnapshot
    && phase === 'idle'
    && !result;
  const poolBlockingMessage = !loading && event?.status === 'CLOSED'
    ? !poolHasParticipants
      ? t('drawNoFinalizedParticipants')
      : !poolMatchesSnapshot
        ? t('drawPoolMismatch')
        : null
    : null;
  const drawAvailabilityMessage = loading
    ? t('drawChecking')
    : event && event.status !== 'CLOSED'
      ? t('drawFinalizeFirst')
      : poolBlockingMessage;
  const drawAvailabilityMessageId = poolBlockingMessage
    ? 'draw-pool-blocker'
    : drawAvailabilityMessage
      ? 'draw-availability-message'
      : undefined;

  const runDraw = useCallback(async () => {
    if (!id || !canDraw) return;

    const names = participants.map(participantName);
    const reduceMotion = prefersReducedMotion();
    setError('');
    setPhase('rolling');
    setDisplayName(reduceMotion ? t('drawValidatingPool') : names[0] || t('drawDrawing'));

    if (!reduceMotion) {
      let cursor = 0;
      spinRef.current = window.setInterval(() => {
        cursor = (cursor + 1) % names.length;
        setDisplayName(names[cursor] || t('drawDrawing'));
      }, ROLL_INTERVAL_MS);
    }

    try {
      const [drawResult] = await Promise.all([
        executeDraw(id),
        reduceMotion ? Promise.resolve() : wait(MINIMUM_ROLL_MS),
      ]);
      stopRolling();
      if (!mountedRef.current) return;

      setResult(drawResult);
      setDisplayName(drawResult.winnerDisplayName || drawResult.winnerUsername || t('drawWinnerFallback'));
      setEvent((current) => current ? {
        ...current,
        status: 'COMPLETED',
        completedAt: drawResult.selectedAt,
      } : current);
      setPhase('done');
    } catch (drawError) {
      stopRolling();
      if (!mountedRef.current) return;
      setDisplayName('');
      setPhase('idle');
      setError(getErrorMessage(drawError, t('drawServerFallback'), t('drawCreatorOnly')));
    }
  }, [canDraw, id, participants, stopRolling, t]);

  const stageLabel = phase === 'rolling'
    ? displayName || t('drawDrawing')
    : result
      ? result.winnerDisplayName || result.winnerUsername
      : loading
        ? t('drawPreparingPool')
        : t('drawReady');

  const winnerName = result?.winnerDisplayName || result?.winnerUsername || '';

  return (
    <div className="hub-page draw-page">
      <header className="page-hero">
        <div className="page-hero-copy">
          <span className="page-eyebrow">{t('drawCreatorArea')}</span>
          <h1 className="page-title">{t('drawHeading')}</h1>
          <p className="page-subtitle">
            {t('drawSubtitle')}
          </p>
        </div>
        {creator && <span className="creator-badge">{t('drawConnectedAs', { creator: creator.displayName })}</span>}
      </header>

      {error && <div className="notice notice-danger" role="alert">{error}</div>}

      {event && (
        <section className="control-card draw-summary" aria-labelledby="draw-event-title">
          <div>
            <span className="section-label">{t('drawFinalizedGiveaway')}</span>
            <h2 id="draw-event-title">{event.title}</h2>
            <p>{event.prize}</p>
          </div>
          <div className="draw-pool-count">
            <strong>{numberFormatter.format(expectedPoolCount)}</strong>
            <span>{t(expectedPoolCount === 1 ? 'drawParticipantOne' : 'drawParticipantMany')}</span>
          </div>
        </section>
      )}

      <section aria-labelledby="draw-stage-title">
        <h2 id="draw-stage-title" className="sr-only">{t('drawStageTitle')}</h2>
        <div
          className={`draw-stage${phase === 'rolling' ? ' rolling' : ''}${phase === 'done' ? ' done' : ''}`}
          aria-busy={phase === 'rolling'}
          aria-live="off"
        >
          <div className="draw-reel">
            <div className="draw-name">{stageLabel}</div>
          </div>
          <div className="draw-glow" aria-hidden="true" />
        </div>
        <p className="sr-only" role="status" aria-live="polite" aria-atomic="true">
          {phase === 'rolling'
            ? t('drawInProgressAnnouncement')
            : phase === 'done'
              ? t('drawCompletedAnnouncement', { winner: winnerName })
              : ''}
        </p>
      </section>

      {!result && (
        <div className="draw-controls">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setManualDonationOpen(true)}
          >
            {t('drawRegisterManual')}
          </button>
          <button
            type="button"
            className="btn btn-primary btn-lg"
            disabled={!canDraw}
            aria-describedby={drawAvailabilityMessageId}
            onClick={() => setConfirmOpen(true)}
          >
            {phase === 'rolling' ? t('drawDrawingOnServer') : t('drawStart')}
          </button>
          {drawAvailabilityMessage && (
            <span
              id={drawAvailabilityMessageId}
              className={poolBlockingMessage
                ? 'form-helper form-helper--err draw-blocking-message'
                : 'form-helper'}
              role={poolBlockingMessage ? 'alert' : 'status'}
            >
              {drawAvailabilityMessage}
            </span>
          )}
        </div>
      )}

      <Modal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        title={t('drawConfirmTitle')}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmOpen(false)}>
              {t('drawBack')}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setConfirmOpen(false);
                void runDraw();
              }}
            >
              {t('drawConfirmAction')}
            </button>
          </>
        )}
      >
        <p>{t(
          expectedPoolCount === 1 ? 'drawConfirmDescriptionOne' : 'drawConfirmDescription',
          { count: numberFormatter.format(expectedPoolCount) },
        )}</p>
      </Modal>
      {manualDonationOpen && (
            <Modal
              open={manualDonationOpen}
              onClose={() => setManualDonationOpen(false)}
              title={t('registerDonationTitle')}
              footer={(
                <>
                  <button type="button" className="btn btn-secondary" onClick={() => setManualDonationOpen(false)}>
                    {t('registerDonationCancel')}
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={!manualUsername.trim() || manualSubmitting}
                    onClick={async () => {
                      if (!id || !manualUsername.trim()) return;
                      setManualSubmitting(true);
                      try {
                        await addManualParticipant(id, manualUsername.trim(), manualActionType, manualAmount);
                        setManualUsername('');
                        setManualAmount(1);
                        setManualDonationOpen(false);
                        addToast('success', t('registerDonationSuccess'));
                        // Refresh participants
                        const updated = await getEventParticipants(id);
                        setParticipants(updated);
                      } catch (e) {
                        setError(getErrorMessage(e, t('registerDonationError'), t('drawCreatorOnly')));
                      } finally {
                        setManualSubmitting(false);
                      }
                    }}
                  >
                    {manualSubmitting ? t('registerDonationSubmitting') : t('registerDonationSubmit')}
                  </button>
                </>
              )}
            >
              <p>{t('registerDonationDescription')}</p>
              <div className="form-group">
                <label htmlFor="donation-username" className="form-label">
                  {t('donationUsernameLabel')}
                </label>
                <input
                  id="donation-username"
                  type="text"
                  className="form-control"
                  placeholder={t('donationUsernamePlaceholder')}
                  value={manualUsername}
                  onChange={(e) => setManualUsername(e.target.value)}
                  disabled={manualSubmitting}
                />
              </div>
              <div className="form-group">
                <label htmlFor="donation-amount" className="form-label">
                  {t('donationAmountLabel')}
                </label>
                <input
                  id="donation-amount"
                  type="number"
                  className="form-control"
                  placeholder={t('donationAmountPlaceholder')}
                  value={manualAmount}
                  onChange={(e) => setManualAmount(Math.max(1, parseInt(e.target.value) || 1))}
                  disabled={manualSubmitting}
                  min="1"
                />
              </div>
              <div className="form-group">
                <label htmlFor="donation-action-type" className="form-label">
                  {t('donationActionTypeLabel')}
                </label>
                <select
                  id="donation-action-type"
                  className="form-control"
                  value={manualActionType}
                  onChange={(e) => setManualActionType(e.target.value)}
                  disabled={manualSubmitting}
                >
                  <option value="donation">{t('donationActionTypeDonation')}</option>
                  <option value="manual">{t('donationActionTypeManual')}</option>
                </select>
              </div>
              {error && <span className="form-helper form-helper--err" role="alert">{error}</span>}
            </Modal>
          )}
      {result && (
        <section className="winner-card" aria-labelledby="draw-winner-title">
          <div className="winner-avatar" aria-hidden="true">{winnerName.slice(0, 1).toUpperCase()}</div>
          <h2 id="draw-winner-title" className="winner-name">{winnerName}</h2>
          <p className="winner-meta">
            {result.winnerUsername ? `@${result.winnerUsername} · ` : ''}
            {t('drawSelectedOn', { date: formatDate(result.selectedAt, lang, t('drawDateUnavailable')) })}
          </p>
          <dl className="proof-grid proof-grid-compact">
            <div><dt>{t('drawMethod')}</dt><dd>{result.drawMethod}</dd></div>
            <div><dt>{t('drawParticipants')}</dt><dd>{numberFormatter.format(result.participantCount)}</dd></div>
          </dl>
          <Link className="btn btn-secondary" to={`/events/${result.eventId}/result`}>
            {t('drawOpenPublicResult')}
          </Link>
        </section>
      )}

      <section className="control-card participant-pool" aria-labelledby="participant-pool-title">
        <div className="section-heading">
          <div>
            <span className="section-label">{t('drawFrozenPool')}</span>
            <h2 id="participant-pool-title">{t('drawBlazeParticipants')}</h2>
          </div>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => setManualDonationOpen(true)}
            aria-label={t('registerDonation')}
          >
            {t('registerDonation')}
          </button>
          <span className="uniform-badge">{t('drawUniformBadge')}</span>
        </div>
        {sortedParticipants.length === 0 ? (
          <div className="empty">{t('drawNoParticipants')}</div>
        ) : (
          <ol className="draw-pool">
            {sortedParticipants.map((participant) => {
              const name = participantName(participant);
              return (
                <li key={participant.blazeUserId} className="draw-pool-item">
                  <span className="dpi-avatar" aria-hidden="true">{name.slice(0, 1).toUpperCase()}</span>
                  <span className="participant-identity">
                    <strong>{name}</strong>
                    {participant.blazeUsername && <small>@{participant.blazeUsername}</small>}
                  </span>
                  <span className="dpi-entries" aria-label={t('drawOneChanceAria')}>{participant.entryWeight}×</span>
                </li>
              );
            })}
          </ol>
        )}
      </section>
    </div>
  );
}
