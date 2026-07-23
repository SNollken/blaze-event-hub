import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ArrowRight, Ban, CircleStop, Radio, Save, ShieldCheck, Trophy } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  cancelEvent,
  getActionRules,
  finalizeEvent,
  getEvent,
  getEventParticipants,
  getEventStats,
  openEvent,
  updateEvent,
  updateActionRules,
} from '../api/client';
import type { EventParticipantResponse, EventResponse, EventStatus } from '../api/types';
import { Modal } from '../components/Modal';
import { addToast, usePolling } from '../components/Toast';
import { getUserFacingErrorMessage } from '../errors/user-facing-error';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';
import { defaultEntryCommand, normalizeXPostUrl } from '../utils/giveaway-form';

const ACTION_TYPES = ['chat', 'vote', 'sub', 'gifted_sub', 'follow', 'donation'] as const;
type ActionTypeValue = typeof ACTION_TYPES[number];
const ACTION_LABELS: Record<ActionTypeValue, { label: string; desc: string }> = {
  chat: { label: 'actionTypeChat', desc: 'actionTypeChatDescription' },
  vote: { label: 'actionTypeVote', desc: 'actionTypeVoteDescription' },
  sub: { label: 'actionTypeSub', desc: 'actionTypeSubDescription' },
  gifted_sub: { label: 'actionTypeGiftedSub', desc: 'actionTypeGiftedSubDescription' },
  follow: { label: 'actionTypeFollow', desc: 'actionTypeFollowDescription' },
  donation: { label: 'actionTypeDonation', desc: 'actionTypeDonationDescription' },
};

type PendingAction = 'save' | 'open' | 'finalize' | 'cancel' | null;

const LIFECYCLE = [
  { status: 'DRAFT', labelKey: 'editStatusDraft' },
  { status: 'OPEN', labelKey: 'editStatusOpen' },
  { status: 'CLOSED', labelKey: 'editStatusClosed' },
  { status: 'COMPLETED', labelKey: 'editStatusCompleted' },
] as const satisfies ReadonlyArray<{ status: EventStatus; labelKey: TranslationKey }>;

function toDateTimeInput(value: string | null): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function toIsoDate(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function toUpdateDate(value: string): string {
  return toIsoDate(value) || '';
}

function formatDate(value: string | null, locale: string, unavailable: string): string {
  if (!value) return unavailable;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return unavailable;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

function statusLabel(status: EventStatus, t: (key: TranslationKey) => string): string {
  if (status === 'FINALIZING') return t('editStatusFinalizing');
  const item = LIFECYCLE.find((candidate) => candidate.status === status);
  return item ? t(item.labelKey) : t('editStatusCancelled');
}

function Lifecycle({ status }: { status: EventStatus }) {
  const { t } = useI18n();
  const lifecycleStatus = status === 'FINALIZING' ? 'OPEN' : status;
  const activeIndex = LIFECYCLE.findIndex((item) => item.status === lifecycleStatus);
  return (
    <ol className="lifecycle" aria-label={t('editLifecycleAria')}>
      {LIFECYCLE.map((item, index) => {
        const stateClass = status === 'CANCELLED'
          ? 'is-muted'
          : index < activeIndex ? 'is-complete' : index === activeIndex ? 'is-current' : '';
        return (
          <li key={item.status} className={stateClass} aria-current={index === activeIndex ? 'step' : undefined}>
            <span>{index + 1}</span>
            <strong>{t(item.labelKey)}</strong>
          </li>
        );
      })}
    </ol>
  );
}

interface OpenEventPanelProps {
  event: EventResponse;
  finalizing: boolean;
  onFinalize: () => Promise<boolean>;
  onCancel: () => void;
  onEventUpdate: (event: EventResponse) => void;
}

const CAPTURE_HEALTH_COPY = {
  INACTIVE: { labelKey: 'editHealthInactiveLabel', descriptionKey: 'editHealthInactiveDescription' },
  STARTING: { labelKey: 'editHealthStartingLabel', descriptionKey: 'editHealthStartingDescription' },
  HEALTHY: { labelKey: 'editHealthHealthyLabel', descriptionKey: 'editHealthHealthyDescription' },
  DEGRADED: { labelKey: 'editHealthDegradedLabel', descriptionKey: 'editHealthDegradedDescription' },
  FINALIZING: { labelKey: 'editHealthFinalizingLabel', descriptionKey: 'editHealthFinalizingDescription' },
} as const;

function OpenEventPanel({ event, finalizing, onFinalize, onCancel, onEventUpdate }: OpenEventPanelProps) {
  const { lang, t } = useI18n();
  const [confirmFinalize, setConfirmFinalize] = useState(false);
  const fetchEvent = useCallback(() => getEvent(event.id), [event.id]);
  const fetchStats = useCallback(() => getEventStats(event.id), [event.id]);
  const fetchParticipants = useCallback(() => getEventParticipants(event.id), [event.id]);
  const eventState = usePolling(fetchEvent, 3_000);
  const stats = usePolling(fetchStats, 5_000);
  const participants = usePolling(fetchParticipants, 5_000);
  const participantCount = stats.data?.participantCount ?? participants.data?.length ?? 0;
  const finalizationInProgress = finalizing || event.status === 'FINALIZING';
  const canFinalize = event.status === 'OPEN' && stats.data?.canFinalize === true && participantCount > 0;
  const captureHealth = stats.data?.captureHealth
    || (event.status === 'FINALIZING' ? 'FINALIZING' : 'STARTING');
  const healthCopy = CAPTURE_HEALTH_COPY[captureHealth];
  const formattedParticipantCount = useMemo(
    () => new Intl.NumberFormat(lang).format(participantCount),
    [lang, participantCount],
  );
  const unavailableDate = t('editDateUnavailable');

  useEffect(() => {
    if (eventState.data && eventState.data.status !== event.status) onEventUpdate(eventState.data);
  }, [event.status, eventState.data, onEventUpdate]);

  const confirm = async () => {
    const completed = await onFinalize();
    if (completed) setConfirmFinalize(false);
  };

  return (
    <div className="control-grid">
      <section className="control-card">
        <div className="section-label">{t('editCaptureState')}</div>
        <div className="signal-command" role="status" aria-live="polite">
          <Radio aria-hidden="true" />
          <code>{event.entryCommand}</code>
        </div>
        <p><strong>{t(healthCopy.labelKey)}.</strong> {t(healthCopy.descriptionKey)} {t('editEveryAccountOnce')}</p>
        <dl className="event-stats">
          <div><dt>{t('editUniqueParticipants')}</dt><dd>{formattedParticipantCount}</dd></div>
          <div><dt>{t('editOpenedAt')}</dt><dd>{formatDate(event.openedAt, lang, unavailableDate)}</dd></div>
          <div><dt>{t('editLastSuccessfulSync')}</dt><dd>{formatDate(stats.data?.lastSuccessfulPollAt || null, lang, unavailableDate)}</dd></div>
          {event.finalizationCutoffAt && <div><dt>{t('editEntriesAcceptedUntil')}</dt><dd>{formatDate(event.finalizationCutoffAt, lang, unavailableDate)}</dd></div>}
        </dl>
        {(stats.data?.lastErrorCode || stats.error || participants.error) && (
          <div className="notice notice-danger" role="alert">
            {t('editSyncAttention')}
          </div>
        )}
        <div className="manage-actions">
          <div className="manage-actions__primary">
            <button
              type="button"
              className="btn btn-primary btn-lg"
              onClick={() => setConfirmFinalize(true)}
              disabled={!canFinalize || finalizationInProgress}
            >
              <CircleStop size={17} aria-hidden="true" />
              {finalizationInProgress ? t('editFinalizingAction') : t('editFinalizeEvent')}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => void Promise.all([stats.reload(), participants.reload()])}
              disabled={stats.loading || participants.loading}
            >
              {t('editRefreshNow')}
            </button>
          </div>
          {event.status === 'OPEN' && (
            <button type="button" className="btn btn-danger manage-actions__danger" onClick={onCancel} disabled={finalizationInProgress}>
              <Ban size={16} aria-hidden="true" /> {t('editCancelEvent')}
            </button>
          )}
        </div>
        {!stats.loading && participantCount === 0 && (
          <p className="form-helper">{t('editWaitForEntry')}</p>
        )}
      </section>

      <section className="control-card">
        <div className="section-label">{t('editConfirmedEntries')}</div>
        {participants.loading && !participants.data ? (
          <div className="empty" role="status">{t('editSyncingParticipants')}</div>
        ) : participants.data?.length ? (
          <ul className="participant-list" aria-live="polite">
            {participants.data.map((participant: EventParticipantResponse) => (
              <li key={participant.blazeUserId} className="participant-item">
                <span aria-hidden="true">{(participant.displayName || participant.blazeUsername || '?')[0].toUpperCase()}</span>
                <div>
                  <strong>{participant.displayName || participant.blazeUsername}</strong>
                  {participant.blazeUsername && <small>@{participant.blazeUsername}</small>}
                  <span className="participant-action-badge">{t((ACTION_LABELS[participant.actionType as ActionTypeValue]?.label ?? 'actionTypeChat') as TranslationKey)}</span>
                </div>
                <time dateTime={participant.enteredAt}>{formatDate(participant.enteredAt, lang, unavailableDate)}</time>
              </li>
            ))}
          </ul>
        ) : (
          <div className="empty">{t('editNoValidEntries')}</div>
        )}
      </section>

      <Modal
        open={confirmFinalize}
        onClose={() => setConfirmFinalize(false)}
        title={t('editFreezePoolTitle')}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmFinalize(false)} disabled={finalizationInProgress}>
              {t('editContinueCapturing')}
            </button>
            <button type="button" className="btn btn-danger" onClick={() => void confirm()} disabled={finalizationInProgress}>
              {finalizationInProgress
                ? t('editFreezing')
                : t(participantCount === 1 ? 'editFinalizeWithOne' : 'editFinalizeWithMany', { count: formattedParticipantCount })}
            </button>
          </>
        )}
      >
        <p><strong>{t('editIrreversible')}</strong> {t('editFreezePoolDescription')}</p>
        <p>{t('editAfterFinalize')}</p>
      </Modal>
    </div>
  );
}

export default function EditEvent() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { lang, t } = useI18n();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [title, setTitle] = useState('');
  const [prize, setPrize] = useState('');
  const [description, setDescription] = useState('');
  const [xPostUrl, setXPostUrl] = useState('');
  const [entryCommand, setEntryCommand] = useState(() => defaultEntryCommand(lang));
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [enabledActionTypes, setEnabledActionTypes] = useState<ActionTypeValue[]>(['chat']);
  const [isLoading, setIsLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [error, setError] = useState('');
  const [xPostUrlInvalid, setXPostUrlInvalid] = useState(false);
  const xPostUrlRef = useRef<HTMLInputElement>(null);
  const currentLangRef = useRef(lang);
  const commandIsAutomaticRef = useRef(true);

  useEffect(() => {
    currentLangRef.current = lang;
    if (commandIsAutomaticRef.current) setEntryCommand(defaultEntryCommand(lang));
  }, [lang]);

  const applyEvent = useCallback((loaded: EventResponse) => {
    setEvent(loaded);
    setTitle(loaded.title);
    setPrize(loaded.prize);
    setDescription(loaded.description || '');
    setXPostUrl(loaded.xPostUrl || '');
    setXPostUrlInvalid(false);
    commandIsAutomaticRef.current = !loaded.entryCommand;
    setEntryCommand(loaded.entryCommand || defaultEntryCommand(currentLangRef.current));
    setStartsAt(toDateTimeInput(loaded.startsAt));
    setEndsAt(toDateTimeInput(loaded.endsAt));
  }, []);

  useEffect(() => {
    let active = true;
    if (!id) {
      setError(t('editInvalidId'));
      setIsLoading(false);
      return () => {
        active = false;
      };
    }

    getEvent(id)
      .then((loaded) => {
        if (active) applyEvent(loaded);
        if (active) {
          getActionRules(id).then((rules) => {
            const enabled = rules.filter((r) => r.enabled).map((r) => r.actionType as ActionTypeValue);
            setEnabledActionTypes(enabled.length > 0 ? enabled : ['chat']);
          }).catch(() => {});
        }
      })
      .catch((loadError) => {
        if (active) setError(getUserFacingErrorMessage(loadError, t('editLoadFallback')));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [applyEvent, id, t]);

  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? t('editDateAfterStart')
      : '';
  }, [endsAt, startsAt, t]);

  const formError = () => {
    if (!title.trim()) return t('editTitleRequired');
    if (!prize.trim()) return t('editPrizeRequired');
    if (xPostUrl.trim() && !normalizeXPostUrl(xPostUrl)) return t('editXPostInvalid');
    if (!/^![\p{L}\p{N}][\p{L}\p{N}_-]{0,78}$/u.test(entryCommand.trim())) {
      return t('editCommandInvalid');
    }
    return dateError;
  };

  const saveDraft = async (): Promise<EventResponse | null> => {
    if (!id || event?.status !== 'DRAFT') return null;
    const invalidXPostUrl = Boolean(xPostUrl.trim() && !normalizeXPostUrl(xPostUrl));
    setXPostUrlInvalid(invalidXPostUrl);
    const invalid = formError();
    if (invalid) {
      setError(invalid);
      if (invalidXPostUrl && title.trim() && prize.trim()) xPostUrlRef.current?.focus();
      return null;
    }

    const updated = await updateEvent(id, {
      title: title.trim(),
      prize: prize.trim(),
      description: description.trim(),
      xPostUrl: normalizeXPostUrl(xPostUrl) || '',
      entryCommand: entryCommand.trim(),
      startsAt: toUpdateDate(startsAt),
      endsAt: toUpdateDate(endsAt),
    });
    applyEvent(updated);
    if (id) {
      await updateActionRules(id, enabledActionTypes).catch(() => {});
    }
    return updated;
  };

  const handleSave = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault();
    setPendingAction('save');
    setError('');
    try {
      const saved = await saveDraft();
      if (saved) addToast('success', t('editDraftSaved'));
    } catch (saveError) {
      setError(getUserFacingErrorMessage(saveError, t('editSaveFallback')));
    } finally {
      setPendingAction(null);
    }
  };

  const handleOpen = async () => {
    if (!id) return;
    setPendingAction('open');
    setError('');
    try {
      const saved = await saveDraft();
      if (!saved) return;
      const opened = await openEvent(id);
      applyEvent(opened);
      setConfirmOpen(false);
      addToast('success', t('editCaptureOpened'));
    } catch (openError) {
      setError(getUserFacingErrorMessage(openError, t('editOpenFallback')));
    } finally {
      setPendingAction(null);
    }
  };

  const handleFinalize = async (): Promise<boolean> => {
    if (!id) return false;
    setPendingAction('finalize');
    setError('');
    try {
      const finalized = await finalizeEvent(id);
      applyEvent(finalized);
      if (finalized.status === 'FINALIZING') {
        addToast('warning', t('editFinalizationStarted'));
      } else {
        addToast('success', t('editPoolReady'));
      }
      return true;
    } catch (finalizeError) {
      setError(getUserFacingErrorMessage(finalizeError, t('editFinalizeFallback')));
      return false;
    } finally {
      setPendingAction(null);
    }
  };

  const handleCancel = async () => {
    if (!id) return;
    setPendingAction('cancel');
    setError('');
    try {
      const cancelled = await cancelEvent(id);
      applyEvent(cancelled);
      setConfirmCancel(false);
      addToast('success', t(event?.status === 'OPEN' ? 'editOpenCancelledToast' : 'editDraftCancelledToast'));
    } catch (cancelError) {
      setError(getUserFacingErrorMessage(cancelError, t('editCancelFallback')));
    } finally {
      setPendingAction(null);
    }
  };

  if (isLoading) return <div className="empty" role="status">{t('editLoading')}</div>;

  if (!event) {
    return (
      <div className="hub-page">
        <div className="notice notice-danger" role="alert">{error || t('editNotFound')}</div>
        <button type="button" className="btn btn-secondary" onClick={() => navigate('/my-events')}>{t('editBackToMine')}</button>
      </div>
    );
  }

  const busy = pendingAction !== null;
  const formattedFinalParticipantCount = new Intl.NumberFormat(lang).format(event.finalizedParticipantCount);

  return (
    <div className="hub-page">
      <header className="page-hero">
        <div>
          <span className={`pill pill--${event.status.toLowerCase()}`}>{statusLabel(event.status, t)}</span>
          <h1 className="page-title">{event.title}</h1>
          <p>{t('editControlCenter')}</p>
        </div>
        <Link className="btn btn-secondary" to={`/events/${event.id}`}>{t('editViewPublic')}</Link>
      </header>

      <Lifecycle status={event.status} />
      {error && <div className="notice notice-danger" role="alert">{error}</div>}

      {event.status === 'DRAFT' && (
        <form className="control-grid manage-draft-grid" onSubmit={handleSave} noValidate>
          <section className="control-card">
            <div className="section-label">{t('editDraftSection')}</div>
            <div className="form-group">
              <label htmlFor="manage-title">{t('editTitleLabel')}</label>
              <input id="manage-title" value={title} onChange={(change) => setTitle(change.target.value)} maxLength={140} disabled={busy} />
            </div>
            <div className="form-group">
              <label htmlFor="manage-prize">{t('editPrizeLabel')}</label>
              <input id="manage-prize" value={prize} onChange={(change) => setPrize(change.target.value)} maxLength={180} disabled={busy} />
            </div>
            <div className="form-group">
              <label htmlFor="manage-description">{t('editDescriptionLabel')}</label>
              <textarea id="manage-description" value={description} onChange={(change) => setDescription(change.target.value)} maxLength={2_000} disabled={busy} />
            </div>
            <div className="form-group">
              <label htmlFor="manage-x-post">{t('editXPostLabel')}</label>
              <input
                ref={xPostUrlRef}
                id="manage-x-post"
                className={xPostUrlInvalid ? 'is-invalid' : undefined}
                type="url"
                inputMode="url"
                value={xPostUrl}
                onChange={(change) => {
                  setXPostUrl(change.target.value);
                  setXPostUrlInvalid(false);
                  setError('');
                }}
                aria-invalid={xPostUrlInvalid}
                aria-describedby={xPostUrlInvalid ? 'manage-x-post-error' : 'manage-x-post-help'}
                maxLength={2_048}
                placeholder={t('editXPostPlaceholder')}
                autoComplete="url"
                disabled={busy}
              />
              {xPostUrlInvalid
                ? <span id="manage-x-post-error" className="form-helper form-helper--err" role="alert">{t('editXPostInvalid')}</span>
                : <span id="manage-x-post-help" className="form-helper">{t('editXPostHelp')}</span>}
            </div>
          </section>

          <section className="control-card">
            <div className="section-label">{t('editCaptureSection')}</div>
            <div className="form-group">
              <label htmlFor="manage-command">{t('editExactCommandLabel')}</label>
              <input
                id="manage-command"
                className="signal-command"
                value={entryCommand}
                onChange={(change) => {
                  commandIsAutomaticRef.current = false;
                  setEntryCommand(change.target.value);
                }}
                maxLength={80}
                autoComplete="off"
                spellCheck={false}
                disabled={busy}
              />
            </div>
            <div className="form-group">
              <label htmlFor="manage-channel">{t('editLinkedChannelLabel')}</label>
              <input
                id="manage-channel"
                value={event.creatorChannelSlug ? `@${event.creatorChannelSlug}` : event.creatorChannelId}
                readOnly
                disabled
              />
              <span className="form-helper">{t('editLinkedChannelHelp')}</span>
            </div>
          </section>

          <section className="control-card manage-schedule-card">
            <div className="section-label">{t('editOptionalSchedule')}</div>
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="manage-starts-at">{t('editStartsAtLabel')}</label>
                <input id="manage-starts-at" type="datetime-local" value={startsAt} onChange={(change) => setStartsAt(change.target.value)} disabled={busy} />
              </div>
              <div className="form-group">
                <label htmlFor="manage-ends-at">{t('editEndsAtLabel')}</label>
                <input id="manage-ends-at" type="datetime-local" value={endsAt} onChange={(change) => setEndsAt(change.target.value)} disabled={busy} />
              </div>
            </div>
            {dateError && <span className="form-helper form-helper--err" role="alert">{dateError}</span>}
          </section>

          <section className="control-card">
            <div className="section-label">{t('actionTypeLabel')}</div>
            <p>{t('actionTypeDescription')}</p>
            <div className="action-type-grid">
              {ACTION_TYPES.map((type) => {
                const keys = ACTION_LABELS[type];
                return (
                  <label key={type} className={`action-type-chip${enabledActionTypes.includes(type) ? ' is-active' : ''}`}>
                    <input
                      type="checkbox"
                      checked={enabledActionTypes.includes(type)}
                      onChange={() => {
                        setEnabledActionTypes((prev) =>
                          prev.includes(type) ? prev.filter((a) => a !== type) : [...prev, type]
                        );
                      }}
                      disabled={busy}
                    />
                    <span className="action-type-chip__label">{t(keys.label as any)}</span>
                    <span className="action-type-chip__desc">{t(keys.desc as any)}</span>
                  </label>
                );
              })}
            </div>
          </section>

          <section className="control-card manage-action-card">
            <div className="section-label">{t('editNextStep')}</div>
            <p>{t('editNextStepDescription')}</p>
            <div className="manage-actions">
              <div className="manage-actions__primary">
                <button type="submit" className="btn btn-secondary" disabled={busy}>
                  <Save size={16} aria-hidden="true" />
                  {pendingAction === 'save' ? t('editSaving') : t('editSaveDraft')}
                </button>
                <button type="button" className="btn btn-primary" onClick={() => setConfirmOpen(true)} disabled={busy || Boolean(formError())}>
                  <Radio size={16} aria-hidden="true" /> {t('editOpenCapture')}
                </button>
              </div>
              <button type="button" className="btn btn-danger manage-actions__danger" onClick={() => setConfirmCancel(true)} disabled={busy}>
                <Ban size={16} aria-hidden="true" /> {t('editCancelEvent')}
              </button>
            </div>
          </section>
        </form>
      )}

      {(event.status === 'OPEN' || event.status === 'FINALIZING') && (
        <OpenEventPanel
          event={event}
          finalizing={pendingAction === 'finalize'}
          onFinalize={handleFinalize}
          onCancel={() => setConfirmCancel(true)}
          onEventUpdate={applyEvent}
        />
      )}

      {event.status === 'CLOSED' && (
        <div className="control-grid">
          <section className="control-card">
            <ShieldCheck size={28} aria-hidden="true" />
            <div className="section-label">{t('editFinalPoolRecorded')}</div>
            <h2>{t(event.finalizedParticipantCount === 1 ? 'editParticipantCountOne' : 'editParticipantCountMany', { count: formattedFinalParticipantCount })}</h2>
            <p>{t('editPoolLockedDescription')}</p>
            <code className="signal-command">{event.finalizedPoolHash || t('editHashUnavailable')}</code>
          </section>
          <section className="control-card">
            <div className="section-label">{t('editReadyToDraw')}</div>
            <p>{t('editDrawOnceDescription')}</p>
            <Link className="btn btn-primary btn-lg" to={`/events/${event.id}/draw`}>
              {t('editGoToDraw')} <ArrowRight size={17} aria-hidden="true" />
            </Link>
          </section>
        </div>
      )}

      {event.status === 'COMPLETED' && (
        <section className="control-card">
          <Trophy size={32} aria-hidden="true" />
          <div className="section-label">{t('editDrawCompleted')}</div>
          <h2>{t('editResultPublished')}</h2>
          <p>{t('editResultDescription')}</p>
          <Link className="btn btn-primary" to={`/events/${event.id}/result`}>
            {t('editViewResult')} <ArrowRight size={17} aria-hidden="true" />
          </Link>
        </section>
      )}

      {event.status === 'CANCELLED' && (
        <section className="control-card">
          <Ban size={28} aria-hidden="true" />
          <div className="section-label">{t('editEventCancelled')}</div>
          <h2>{t('editEventClosed')}</h2>
          <p>{t('editEventReadOnly')}</p>
          <Link className="btn btn-secondary" to="/my-events">{t('editBackToMine')}</Link>
        </section>
      )}

      <Modal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        title={t('editOpenModalTitle')}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmOpen(false)} disabled={busy}>{t('editNotYet')}</button>
            <button type="button" className="btn btn-primary" onClick={() => void handleOpen()} disabled={busy}>
              <Radio size={16} aria-hidden="true" /> {pendingAction === 'open' ? t('editOpening') : t('editOpenCapture')}
            </button>
          </>
        )}
      >
        <p>{t('editOpenModalPrefix')} <strong>{entryCommand}</strong> {t('editOpenModalSuffix')}</p>
      </Modal>

      <Modal
        open={confirmCancel}
        onClose={() => setConfirmCancel(false)}
        title={t(event.status === 'OPEN' ? 'editCancelOpenTitle' : 'editCancelDraftTitle')}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmCancel(false)} disabled={busy}>
              {t(event.status === 'OPEN' ? 'editKeepCapture' : 'editKeepDraft')}
            </button>
            <button type="button" className="btn btn-danger" onClick={() => void handleCancel()} disabled={busy}>
              <Ban size={16} aria-hidden="true" /> {pendingAction === 'cancel' ? t('editCancelling') : t('editCancelEvent')}
            </button>
          </>
        )}
      >
        <p>
          {t(event.status === 'OPEN' ? 'editCancelOpenDescription' : 'editCancelDraftDescription')}
        </p>
      </Modal>
    </div>
  );
}
