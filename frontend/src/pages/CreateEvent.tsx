import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ArrowRight, Radio } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, createEvent, getMe, updateActionRules } from '../api/client';
import type { MemberProfile } from '../api/types';
import { addToast } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';
import { defaultEntryCommand, normalizeXPostUrl } from '../utils/giveaway-form';

type FieldName = 'title' | 'prize' | 'xPostUrl' | 'entryCommand';
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
type FieldErrors = Partial<Record<FieldName, string>>;

function toIsoDate(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function isBlazeConnectionError(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false;
  return error.status === 401
    || error.code === 'AUTHENTICATION_REQUIRED'
    || error.code === 'FORBIDDEN'
    || error.code === 'BLAZE_API_ERROR'
    || error.code === 'CONFIG_MISSING'
    || error.code.startsWith('BLAZE_TOKEN_')
    || error.code.startsWith('OAUTH_');
}

export default function CreateEvent() {
  const navigate = useNavigate();
  const { lang, t } = useI18n();
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [memberLoading, setMemberLoading] = useState(true);
  const [memberUnavailable, setMemberUnavailable] = useState(false);
  const [title, setTitle] = useState('');
  const [prize, setPrize] = useState('');
  const [description, setDescription] = useState('');
  const [xPostUrl, setXPostUrl] = useState('');
  const [entryCommand, setEntryCommand] = useState(() => defaultEntryCommand(lang));
  const [enabledActionTypes, setEnabledActionTypes] = useState<ActionTypeValue[]>(['chat']);
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [showReconnectAction, setShowReconnectAction] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const titleRef = useRef<HTMLInputElement>(null);
  const prizeRef = useRef<HTMLInputElement>(null);
  const xPostUrlRef = useRef<HTMLInputElement>(null);
  const commandRef = useRef<HTMLInputElement>(null);
  const endsAtRef = useRef<HTMLInputElement>(null);
  const commandIsAutomaticRef = useRef(true);
  const previousLangRef = useRef(lang);

  useEffect(() => {
    if (previousLangRef.current !== lang && commandIsAutomaticRef.current) {
      setEntryCommand(defaultEntryCommand(lang));
    }
    previousLangRef.current = lang;
    setError('');
    setShowReconnectAction(false);
    setFieldErrors({});
  }, [lang]);

  useEffect(() => {
    let active = true;
    getMe()
      .then((profile) => {
        if (!active) return;
        setMember(profile);
        setMemberUnavailable(false);
        setError('');
        setShowReconnectAction(false);
      })
      .catch(() => {
        if (!active) return;
        setMember(null);
        setMemberUnavailable(true);
        setError('');
        setShowReconnectAction(false);
      })
      .finally(() => {
        if (active) setMemberLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? t('createDateAfterStart')
      : '';
  }, [endsAt, startsAt, t]);

  const validateFields = (): FieldErrors => {
    const next: FieldErrors = {};
    if (!title.trim()) next.title = t('createTitleRequired');
    if (!prize.trim()) next.prize = t('createPrizeRequired');
    if (xPostUrl.trim() && !normalizeXPostUrl(xPostUrl)) {
      next.xPostUrl = t('createXPostInvalid');
    }
    if (!/^![\p{L}\p{N}][\p{L}\p{N}_-]{0,78}$/u.test(entryCommand.trim())) {
      next.entryCommand = t('createCommandInvalid');
    }
    return next;
  };

  const clearFieldError = (field: FieldName) => {
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setError('');
    setShowReconnectAction(false);
  };

  const clearFormError = () => {
    setError('');
    setShowReconnectAction(false);
  };

  const handleSubmit = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault();
    const invalidFields = validateFields();
    setFieldErrors(invalidFields);
    if (Object.keys(invalidFields).length > 0 || dateError) {
      setError(t('createReviewFields'));
      setShowReconnectAction(false);
      const firstInvalidField = invalidFields.title
        ? titleRef.current
        : invalidFields.prize
          ? prizeRef.current
          : invalidFields.xPostUrl
            ? xPostUrlRef.current
            : invalidFields.entryCommand
              ? commandRef.current
              : endsAtRef.current;
      firstInvalidField?.focus();
      return;
    }
    if (memberLoading) {
      setError(t('createAccountLoading'));
      setShowReconnectAction(false);
      return;
    }
    if (!member) {
      setError(t('createAccountUnavailable'));
      setShowReconnectAction(true);
      return;
    }

    setIsSubmitting(true);
    setError('');
    setShowReconnectAction(false);
    try {
      const created = await createEvent({
        title: title.trim(),
        prize: prize.trim(),
        description: description.trim() || undefined,
        xPostUrl: normalizeXPostUrl(xPostUrl) || undefined,
        entryCommand: entryCommand.trim(),
        startsAt: toIsoDate(startsAt),
        endsAt: toIsoDate(endsAt),
      });
      if (enabledActionTypes.length > 0) {
        await updateActionRules(created.id, enabledActionTypes);
      }
      addToast('success', t('createSuccessToast'));
      navigate(`/events/${created.id}/manage`);
    } catch (submitError) {
      const connectionFailure = isBlazeConnectionError(submitError);
      setError(connectionFailure ? t('createConnectionError') : t('createSubmitFallback'));
      setShowReconnectAction(connectionFailure);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="hub-page">
      <header className="page-hero create-page-hero">
        <div>
          <span className="section-label">{t('createEyebrow')}</span>
          <h1 className="page-title">{t('createHeading')}</h1>
          <p>{t('createSubtitle')}</p>
        </div>
        {member && (
          <div className="creator-chip" aria-label={t('createConnectedCreatorAria')}>
            <span className="creator-chip__signal" aria-hidden="true" />
            <span className="section-label">{t('createConnectedCreatorLabel')}</span>
            <div className="creator-identity">
              <strong>{member.displayName || member.blazeUsername}</strong>
              <span className="creator-handle">@{member.blazeUsername}</span>
            </div>
          </div>
        )}
      </header>

      {error && (
        <div className="notice notice-danger create-error-notice" role="alert">
          <span>{error}</span>
          {showReconnectAction && <Link to="/settings/blaze">{t('createReconnectBlaze')}</Link>}
        </div>
      )}
      {memberUnavailable && (
        <div className="notice notice-danger create-error-notice" role="alert">
          <span>{t('createAccountUnavailable')}</span>
          <Link to="/settings/blaze">{t('createReconnectBlaze')}</Link>
        </div>
      )}

      <form className="create-workbench" onSubmit={handleSubmit} noValidate>
        <section className="create-sheet create-sheet--details" data-index="01">
          <div className="section-label">{t('createPublicInfo')}</div>
          <div className="form-group">
            <label htmlFor="event-title">{t('createEventTitleLabel')}</label>
            <input
              ref={titleRef}
              id="event-title"
              className={fieldErrors.title ? 'is-invalid' : undefined}
              value={title}
              onChange={(event) => {
                setTitle(event.target.value);
                clearFieldError('title');
              }}
              aria-invalid={Boolean(fieldErrors.title)}
              aria-describedby={fieldErrors.title ? 'event-title-error' : undefined}
              maxLength={140}
              placeholder={t('createTitlePlaceholder')}
              disabled={isSubmitting}
              required
            />
            {fieldErrors.title && <span id="event-title-error" className="form-helper form-helper--err" role="alert">{fieldErrors.title}</span>}
          </div>
          <div className="form-group">
            <label htmlFor="event-prize">{t('createPrizeLabel')}</label>
            <input
              ref={prizeRef}
              id="event-prize"
              className={fieldErrors.prize ? 'is-invalid' : undefined}
              value={prize}
              onChange={(event) => {
                setPrize(event.target.value);
                clearFieldError('prize');
              }}
              aria-invalid={Boolean(fieldErrors.prize)}
              aria-describedby={fieldErrors.prize ? 'event-prize-error' : undefined}
              maxLength={180}
              placeholder={t('createPrizePlaceholder')}
              disabled={isSubmitting}
              required
            />
            {fieldErrors.prize && <span id="event-prize-error" className="form-helper form-helper--err" role="alert">{fieldErrors.prize}</span>}
          </div>
          <div className="form-group">
            <label htmlFor="event-description">{t('createDescriptionLabel')}</label>
            <textarea
              id="event-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={2_000}
              placeholder={t('createDescriptionPlaceholder')}
              disabled={isSubmitting}
            />
          </div>
          <div className="form-group">
            <label htmlFor="event-x-post">{t('createXPostLabel')}</label>
            <input
              ref={xPostUrlRef}
              id="event-x-post"
              className={fieldErrors.xPostUrl ? 'is-invalid' : undefined}
              type="url"
              inputMode="url"
              value={xPostUrl}
              onChange={(event) => {
                setXPostUrl(event.target.value);
                clearFieldError('xPostUrl');
              }}
              aria-invalid={Boolean(fieldErrors.xPostUrl)}
              aria-describedby={fieldErrors.xPostUrl ? 'event-x-post-error' : 'event-x-post-help'}
              maxLength={2_048}
              placeholder={t('createXPostPlaceholder')}
              autoComplete="url"
              disabled={isSubmitting}
            />
            {fieldErrors.xPostUrl
              ? <span id="event-x-post-error" className="form-helper form-helper--err" role="alert">{fieldErrors.xPostUrl}</span>
              : <span id="event-x-post-help" className="form-helper">{t('createXPostHelp')}</span>}
          </div>
        </section>

        <section className="create-sheet create-sheet--signal" data-index="02">
          <div className="section-label">{t('createEntrySignal')}</div>
          <p>{t('createEntrySignalDescription')}</p>
          <div className="form-group">
            <label htmlFor="event-command">{t('createChatCommandLabel')}</label>
            <input
              ref={commandRef}
              id="event-command"
              className={`signal-command${fieldErrors.entryCommand ? ' is-invalid' : ''}`}
              value={entryCommand}
              onChange={(event) => {
                commandIsAutomaticRef.current = false;
                setEntryCommand(event.target.value);
                clearFieldError('entryCommand');
              }}
              aria-invalid={Boolean(fieldErrors.entryCommand)}
              aria-describedby={fieldErrors.entryCommand ? 'event-command-error' : undefined}
              maxLength={80}
              autoComplete="off"
              spellCheck={false}
              disabled={isSubmitting}
              required
            />
            {fieldErrors.entryCommand && <span id="event-command-error" className="form-helper form-helper--err" role="alert">{fieldErrors.entryCommand}</span>}
          </div>

          <div className="connected-channel-card" role="group" aria-label={t('createConnectedChannelAria')}>
            <span className="connected-channel-card__avatar" aria-hidden="true">
              {member?.avatarUrl
                ? <img src={member.avatarUrl} alt="" />
                : (member?.displayName || member?.blazeUsername || '?')[0]?.toUpperCase()}
            </span>
            <div className="connected-channel-card__body">
              <span className="section-label">{t('createConnectedChannelLabel')}</span>
              {memberLoading && <strong>{t('createLoadingChannel')}</strong>}
              {!memberLoading && member && <strong>@{member.blazeUsername}</strong>}
              {!memberLoading && !member && <strong>{t('createChannelUnavailable')}</strong>}
              <small>{member ? t('createConnectedChannelHelp') : t('createReconnectChannelHelp')}</small>
            </div>
          </div>
        </section>

        <section className="create-sheet create-sheet--actions" data-index="03">
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
                    disabled={isSubmitting}
                  />
                  <span className="action-type-chip__label">{t(keys.label as any)}</span>
                  <span className="action-type-chip__desc">{t(keys.desc as any)}</span>
                </label>
              );
            })}
          </div>
        </section>

        <section className="create-sheet create-sheet--schedule" data-index="04">
          <div className="section-label">{t('createOptionalSchedule')}</div>
          <p>{t('createScheduleDescription')}</p>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="event-starts-at">{t('createStartsAtLabel')}</label>
              <input
                id="event-starts-at"
                className={dateError ? 'is-invalid' : undefined}
                type="datetime-local"
                value={startsAt}
                onChange={(event) => {
                  setStartsAt(event.target.value);
                  clearFormError();
                }}
                aria-invalid={Boolean(dateError)}
                aria-describedby={dateError ? 'event-date-error' : undefined}
                disabled={isSubmitting}
              />
            </div>
            <div className="form-group">
              <label htmlFor="event-ends-at">{t('createEndsAtLabel')}</label>
              <input
                ref={endsAtRef}
                id="event-ends-at"
                className={dateError ? 'is-invalid' : undefined}
                type="datetime-local"
                value={endsAt}
                onChange={(event) => {
                  setEndsAt(event.target.value);
                  clearFormError();
                }}
                aria-invalid={Boolean(dateError)}
                aria-describedby={dateError ? 'event-date-error' : undefined}
                disabled={isSubmitting}
              />
            </div>
          </div>
          {dateError && <span id="event-date-error" className="form-helper form-helper--err" role="alert">{dateError}</span>}
        </section>

        <section className="create-launch-panel">
          <div className="create-launch-panel__note">
            <span className="create-launch-panel__index">{t('createDraftIndex')}</span>
            <Radio aria-hidden="true" />
            <div>
              <strong>{t('createDraftNoteTitle')}</strong>
              <p>{t('createDraftNoteText')}</p>
            </div>
          </div>
          <div className="form-actions create-launch-panel__actions">
            <button
              type="submit"
              className="btn btn-primary btn-lg"
              disabled={isSubmitting || memberLoading || memberUnavailable}
            >
              {isSubmitting ? t('createCreatingDraft') : t('createSubmit')}
              {!isSubmitting && <ArrowRight size={17} aria-hidden="true" />}
            </button>
            <button type="button" className="btn btn-secondary btn-lg" onClick={() => navigate(-1)} disabled={isSubmitting}>
              {t('createBack')}
            </button>
          </div>
        </section>
      </form>
    </div>
  );
}
