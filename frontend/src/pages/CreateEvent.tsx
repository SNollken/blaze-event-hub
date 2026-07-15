import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { ArrowRight, CheckCircle2, Radio, Search } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { createEvent, getMe, resolveBlazeChannel } from '../api/client';
import type { BlazeChannelResponse, MemberProfile } from '../api/types';
import { addToast } from '../components/Toast';
import { getUserFacingErrorMessage } from '../errors/user-facing-error';
import { useI18n } from '../i18n/I18nContext';

type FieldName = 'title' | 'prize' | 'entryCommand' | 'channel';
type FieldErrors = Partial<Record<FieldName, string>>;

function toIsoDate(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function normalizeChannelSlug(value: string): string {
  const withoutOrigin = value.trim().replace(/^(?:https?:\/\/)?(?:www\.)?blaze\.stream\//i, '');
  return withoutOrigin.split(/[/?#]/, 1)[0].replace(/^@/, '').trim();
}

export default function CreateEvent() {
  const navigate = useNavigate();
  const { lang, t } = useI18n();
  const [member, setMember] = useState<MemberProfile | null>(null);
  const [title, setTitle] = useState('');
  const [prize, setPrize] = useState('');
  const [description, setDescription] = useState('');
  const [entryCommand, setEntryCommand] = useState('!participar');
  const [channelSlug, setChannelSlug] = useState('');
  const [resolvedChannel, setResolvedChannel] = useState<BlazeChannelResponse | null>(null);
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [isResolving, setIsResolving] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [channelError, setChannelError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  useEffect(() => {
    setError('');
    setChannelError('');
    setFieldErrors({});
  }, [lang]);

  useEffect(() => {
    let active = true;
    getMe()
      .then((profile) => {
        if (active) setMember(profile);
      })
      .catch(() => {
        if (active) setMember(null);
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

  const resolveChannel = async () => {
    const slug = normalizeChannelSlug(channelSlug);
    if (!slug) {
      setResolvedChannel(null);
      setChannelError(t('createChannelSlugRequired'));
      setFieldErrors((current) => ({ ...current, channel: t('createChannelResolveRequired') }));
      return;
    }

    setIsResolving(true);
    setChannelError('');
    setResolvedChannel(null);
    try {
      const channel = await resolveBlazeChannel(slug);
      setChannelSlug(channel.slug);
      setResolvedChannel(channel);
      setFieldErrors((current) => ({ ...current, channel: undefined }));
    } catch (resolveError) {
      setChannelError(getUserFacingErrorMessage(resolveError, t('createChannelResolveFallback')));
    } finally {
      setIsResolving(false);
    }
  };

  const validateFields = (): FieldErrors => {
    const next: FieldErrors = {};
    if (!title.trim()) next.title = t('createTitleRequired');
    if (!prize.trim()) next.prize = t('createPrizeRequired');
    if (!/^![\p{L}\p{N}][\p{L}\p{N}_-]{0,78}$/u.test(entryCommand.trim())) {
      next.entryCommand = t('createCommandInvalid');
    }
    if (!resolvedChannel) next.channel = t('createChannelConfirmRequired');
    return next;
  };

  const clearFieldError = (field: FieldName) => {
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
  };

  const handleSubmit = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault();
    const invalidFields = validateFields();
    setFieldErrors(invalidFields);
    if (Object.keys(invalidFields).length > 0 || dateError) {
      setError(t('createReviewFields'));
      return;
    }
    if (!resolvedChannel) return;

    setIsSubmitting(true);
    setError('');
    try {
      const created = await createEvent({
        title: title.trim(),
        prize: prize.trim(),
        description: description.trim() || undefined,
        entryCommand: entryCommand.trim(),
        creatorChannelSlug: resolvedChannel.slug,
        startsAt: toIsoDate(startsAt),
        endsAt: toIsoDate(endsAt),
      });
      addToast('success', t('createSuccessToast'));
      navigate(`/events/${created.id}/manage`);
    } catch (submitError) {
      setError(getUserFacingErrorMessage(submitError, t('createSubmitFallback')));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="hub-page">
      <header className="page-hero">
        <div>
          <span className="section-label">{t('createEyebrow')}</span>
          <h1 className="page-title">{t('createHeading')}</h1>
          <p>{t('createSubtitle')}</p>
        </div>
        {member && (
          <div className="control-card creator-card" aria-label={t('createConnectedCreatorAria')}>
            <span className="section-label">{t('createConnectedCreatorLabel')}</span>
            <div className="creator-identity">
              <strong>{member.displayName || member.blazeUsername}</strong>
              <span className="creator-handle">@{member.blazeUsername}</span>
            </div>
          </div>
        )}
      </header>

      {error && <div className="notice notice-danger" role="alert">{error}</div>}

      <form className="control-grid" onSubmit={handleSubmit} noValidate>
        <section className="control-card">
          <div className="section-label">{t('createPublicInfo')}</div>
          <div className="form-group">
            <label htmlFor="event-title">{t('createEventTitleLabel')}</label>
            <input
              id="event-title"
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
              id="event-prize"
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
        </section>

        <section className="control-card">
          <div className="section-label">{t('createEntrySignal')}</div>
          <p>{t('createEntrySignalDescription')}</p>
          <div className="form-group">
            <label htmlFor="event-command">{t('createChatCommandLabel')}</label>
            <input
              id="event-command"
              className="signal-command"
              value={entryCommand}
              onChange={(event) => {
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

          <div className="form-group">
            <label htmlFor="event-channel">{t('createBlazeChannelLabel')}</label>
            <div className="form-row">
              <input
                id="event-channel"
                value={channelSlug}
                onChange={(event) => {
                  setChannelSlug(event.target.value);
                  setResolvedChannel(null);
                  setChannelError('');
                  clearFieldError('channel');
                }}
                aria-invalid={Boolean(channelError || fieldErrors.channel)}
                aria-describedby={channelError || fieldErrors.channel ? 'event-channel-error' : undefined}
                placeholder={t('createChannelPlaceholder')}
                maxLength={180}
                autoComplete="off"
                disabled={isSubmitting || isResolving}
              />
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => void resolveChannel()}
                disabled={isSubmitting || isResolving || !channelSlug.trim()}
              >
                <Search size={16} aria-hidden="true" />
                {isResolving ? t('createResolvingChannel') : t('createResolveChannel')}
              </button>
            </div>
            {(channelError || fieldErrors.channel) && (
              <span id="event-channel-error" className="form-helper form-helper--err" role="alert">
                {channelError || fieldErrors.channel}
              </span>
            )}
          </div>

          {resolvedChannel && (
            <div className="control-card" role="status">
              <CheckCircle2 size={18} aria-hidden="true" />
              <div>
                <strong>{resolvedChannel.displayName}</strong>
                <span>@{resolvedChannel.slug}</span>
                <code>{resolvedChannel.id}</code>
              </div>
            </div>
          )}
        </section>

        <section className="control-card">
          <div className="section-label">{t('createOptionalSchedule')}</div>
          <p>{t('createScheduleDescription')}</p>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="event-starts-at">{t('createStartsAtLabel')}</label>
              <input
                id="event-starts-at"
                type="datetime-local"
                value={startsAt}
                onChange={(event) => setStartsAt(event.target.value)}
                aria-invalid={Boolean(dateError)}
                aria-describedby={dateError ? 'event-date-error' : undefined}
                disabled={isSubmitting}
              />
            </div>
            <div className="form-group">
              <label htmlFor="event-ends-at">{t('createEndsAtLabel')}</label>
              <input
                id="event-ends-at"
                type="datetime-local"
                value={endsAt}
                onChange={(event) => setEndsAt(event.target.value)}
                aria-invalid={Boolean(dateError)}
                aria-describedby={dateError ? 'event-date-error' : undefined}
                disabled={isSubmitting}
              />
            </div>
          </div>
          {dateError && <span id="event-date-error" className="form-helper form-helper--err" role="alert">{dateError}</span>}
        </section>

        <section className="control-card">
          <div className="section-label">{t('createHowItWorks')}</div>
          <ol className="lifecycle">
            <li className="is-current"><Radio aria-hidden="true" /><span><strong>{t('createLifecycleOpenTitle')}</strong> {t('createLifecycleOpenText')}</span></li>
            <li><span><strong>{t('createLifecycleReceiveTitle')}</strong> {t('createLifecycleReceiveText')}</span></li>
            <li><span><strong>{t('createLifecycleFinalizeTitle')}</strong> {t('createLifecycleFinalizeText')}</span></li>
            <li><span><strong>{t('createLifecycleDrawTitle')}</strong> {t('createLifecycleDrawText')}</span></li>
          </ol>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary btn-lg" disabled={isSubmitting}>
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
