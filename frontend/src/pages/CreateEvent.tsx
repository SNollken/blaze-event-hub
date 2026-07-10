import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../i18n/I18nContext';
import { createEvent, getMe } from '../api/client';
import type { CreateRuleRequest, MemberProfile } from '../api/types';
import type { TranslationKey } from '../i18n/translations';

type ActionType = 'vote' | 'sub' | 'gifted_sub';

type RuleDraft = {
  actionType: ActionType;
  thresholdAmount: number;
  entries: number;
};

type Translate = (key: TranslationKey, params?: Record<string, string | number>) => string;

const ACTION_OPTIONS: Array<{ value: ActionType; labelKey: TranslationKey; unitKey: TranslationKey }> = [
  { value: 'vote', labelKey: 'actionVote', unitKey: 'actionVote' },
  { value: 'sub', labelKey: 'actionSub', unitKey: 'actionSub' },
  { value: 'gifted_sub', labelKey: 'actionGiftedSub', unitKey: 'actionGiftedSub' },
];

const defaultRule = (): RuleDraft => ({
  actionType: 'vote',
  thresholdAmount: 50,
  entries: 1,
});

function positiveInteger(value: number) {
  return Number.isFinite(value) && Number.isInteger(value) && value > 0;
}

function toIsoDate(value: string) {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function actionUnit(t: Translate, actionType: ActionType) {
  const option = ACTION_OPTIONS.find((candidate) => candidate.value === actionType);
  return option ? t(option.unitKey) : actionType;
}

function validateRules(rules: RuleDraft[], t: Translate) {
  if (rules.length === 0) return t('ruleRequired');
  if (rules.some((rule) => !rule.actionType)) return t('ruleActionRequired');
  if (rules.some((rule) => !positiveInteger(rule.thresholdAmount))) {
    return t('ruleThresholdInvalid');
  }
  if (rules.some((rule) => !positiveInteger(rule.entries))) {
    return t('ruleEntriesInvalid');
  }
  return '';
}

export default function CreateEvent() {
  const navigate = useNavigate();
  const { t } = useI18n();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [me, setMe] = useState<MemberProfile | null>(null);
  const [isLoadingMe, setIsLoadingMe] = useState(true);
  const [meError, setMeError] = useState('');
  const [rulesMode, setRulesMode] = useState('tier');
  const [maxEntriesPerParticipant, setMaxEntriesPerParticipant] = useState(0);
  const [requiresInterestBeforeAction, setRequiresInterestBeforeAction] = useState(false);
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [rules, setRules] = useState<RuleDraft[]>([defaultRule()]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;

    getMe()
      .then((profile) => {
        if (!ignore) setMe(profile);
      })
      .catch((err) => {
        if (!ignore) {
          setMe(null);
          setMeError(err instanceof Error ? err.message : t('creatorChannelLoadError'));
        }
      })
      .finally(() => {
        if (!ignore) setIsLoadingMe(false);
      });

    return () => {
      ignore = true;
    };
  }, [t]);

  const rulesError = useMemo(() => validateRules(rules, t), [rules, t]);
  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? t('dateOrderError')
      : '';
  }, [startsAt, endsAt, t]);

  const canSubmit = Boolean(title.trim())
    && Boolean(me)
    && !rulesError
    && !dateError
    && !isLoadingMe
    && !isSubmitting;

  const updateRule = <K extends keyof RuleDraft>(index: number, field: K, value: RuleDraft[K]) => {
    setRules((current) => current.map((rule, ruleIndex) => (
      ruleIndex === index ? { ...rule, [field]: value } : rule
    )));
  };

  const addRule = () => {
    setRules((current) => [...current, { actionType: 'vote', thresholdAmount: 100, entries: 3 }]);
  };

  const removeRule = (index: number) => {
    setRules((current) => (current.length === 1 ? current : current.filter((_, ruleIndex) => ruleIndex !== index)));
  };

  const validateForm = () => {
    if (!title.trim()) return t('titleRequired');
    if (isLoadingMe) return t('creatorChannelLoading');
    if (!me) return meError || t('creatorChannelUnavailable');
    if (rulesError) return rulesError;
    if (dateError) return dateError;
    return '';
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    setIsSubmitting(true);
    setError('');

    if (!me) {
      setError(t('creatorChannelUnavailable'));
      setIsSubmitting(false);
      return;
    }

    const payload = {
      title: title.trim(),
      description: description.trim() || undefined,
      creatorChannelId: me.blazeUserId,
      rulesMode,
      maxEntriesPerParticipant: Math.max(0, Math.trunc(maxEntriesPerParticipant || 0)),
      requiresInterestBeforeAction,
      startsAt: toIsoDate(startsAt),
      endsAt: toIsoDate(endsAt),
      rules: rules.map<CreateRuleRequest>((rule) => ({
        actionType: rule.actionType,
        thresholdAmount: Math.trunc(rule.thresholdAmount),
        entries: Math.trunc(rule.entries),
      })),
    };

    try {
      const created = await createEvent(payload);
      navigate(`/events/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('createEventError'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: 760 }}>
      <h1 className="page-title">{t('createTitle')}</h1>
      <p className="page-subtitle">{t('createSubtitle')}</p>

      {error && <div className="toast toast-error" style={{ position: 'static', marginBottom: 24 }}>{error}</div>}

      <form onSubmit={handleSubmit} autoComplete="off">
        <div className="form-section">
          <label className="form-label" htmlFor="event-title">{t('title')}</label>
          <div className="form-field">
            <input
              id="event-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder={t('titlePh')}
              disabled={isSubmitting}
              required
            />
          </div>
        </div>

        <div className="form-section">
          <label className="form-label" htmlFor="event-description">{t('description')}</label>
          <div className="form-field">
            <textarea
              id="event-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder={t('descPh')}
              disabled={isSubmitting}
            />
          </div>
        </div>

        <div className="form-section">
          <div className="form-label">{t('channelBlaze')}</div>
          {isLoadingMe && <div className="form-helper">{t('creatorChannelLoading')}</div>}
          {meError && <div className="form-helper form-helper--err">{meError}</div>}
          {me && (
            <div className="channel-preview">
              {me.avatarUrl ? (
                <img src={me.avatarUrl} alt="" />
              ) : (
                <div
                  aria-hidden="true"
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: '50%',
                    background: 'var(--accent-bg)',
                    color: 'var(--accent-light)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 600,
                    flexShrink: 0,
                  }}
                >
                  {(me.displayName || me.blazeUsername || '?')[0].toUpperCase()}
                </div>
              )}
              <div>
                <div className="ch-name">{me.displayName || me.blazeUsername}</div>
                <div className="ch-meta">
                  @{me.blazeUsername}
                  <span style={{ opacity: 0.35 }}>.</span>
                  <span className="ch-id">{me.blazeUserId}</span>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="form-section">
          <div className="form-row">
            <div>
              <label className="form-label" htmlFor="event-rules-mode">{t('rulesMode')}</label>
              <div className="form-field">
                <select
                  id="event-rules-mode"
                  value={rulesMode}
                  onChange={(event) => setRulesMode(event.target.value)}
                  disabled={isSubmitting}
                >
                  <option value="tier">{t('modeTier')}</option>
                  <option value="cumulative">{t('modeCumulative')}</option>
                </select>
              </div>
            </div>
            <div>
              <label className="form-label" htmlFor="event-max-entries">{t('maxEntries')}</label>
              <div className="form-field">
                <input
                  id="event-max-entries"
                  type="number"
                  min={0}
                  value={maxEntriesPerParticipant || ''}
                  onChange={(event) => setMaxEntriesPerParticipant(Number(event.target.value) || 0)}
                  placeholder={t('maxEntriesPh')}
                  disabled={isSubmitting}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="form-section">
          <div className="form-row">
            <div>
              <label className="form-label" htmlFor="event-starts-at">{t('startAt')}</label>
              <div className="form-field">
                <input
                  id="event-starts-at"
                  type="datetime-local"
                  value={startsAt}
                  onChange={(event) => setStartsAt(event.target.value)}
                  disabled={isSubmitting}
                />
              </div>
            </div>
            <div>
              <label className="form-label" htmlFor="event-ends-at">{t('endAt')}</label>
              <div className="form-field">
                <input
                  id="event-ends-at"
                  type="datetime-local"
                  value={endsAt}
                  onChange={(event) => setEndsAt(event.target.value)}
                  disabled={isSubmitting}
                />
              </div>
            </div>
          </div>
          {dateError && <div className="form-helper form-helper--err">{dateError}</div>}
        </div>

        <div className="form-section">
          <label className="flex items-center gap-sm" style={{ color: 'var(--fg2)' }}>
            <input
              type="checkbox"
              checked={requiresInterestBeforeAction}
              onChange={(event) => setRequiresInterestBeforeAction(event.target.checked)}
              disabled={isSubmitting}
            />
            {t('requiresInterest')}
          </label>
        </div>

        <div className="form-section">
          <div className="section-header" style={{ marginBottom: 12 }}>
            <label className="form-label" style={{ margin: 0 }}>{t('rulesOfEntries')}</label>
            <button type="button" className="btn-add-rule" onClick={addRule} disabled={isSubmitting}>
              {t('addRule')}
            </button>
          </div>

          <div className="flex flex-col gap-sm">
            {rules.map((rule, index) => (
              <div className="rule-card" key={`${rule.actionType}-${index}`}>
                <span className="r-sep">{t('each')}</span>
                <input
                  className="r-input"
                  type="number"
                  min={1}
                  value={rule.thresholdAmount || ''}
                  onChange={(event) => updateRule(index, 'thresholdAmount', Number(event.target.value) || 0)}
                  disabled={isSubmitting}
                />
                <select
                  className="r-input"
                  style={{ width: 132 }}
                  value={rule.actionType}
                  onChange={(event) => updateRule(index, 'actionType', event.target.value as ActionType)}
                  disabled={isSubmitting}
                >
                  {ACTION_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{t(option.labelKey)}</option>
                  ))}
                </select>
                <span className="r-sep">{t('equals')}</span>
                <input
                  className="r-input"
                  type="number"
                  min={1}
                  value={rule.entries || ''}
                  onChange={(event) => updateRule(index, 'entries', Number(event.target.value) || 0)}
                  disabled={isSubmitting}
                />
                <span className="r-sep">
                  {t('ruleEntriesPerAction', {
                    entries: rule.entries,
                    entryLabel: t('entriesUnit'),
                    action: actionUnit(t, rule.actionType),
                  })}
                </span>
                <button
                  type="button"
                  className="r-close"
                  onClick={() => removeRule(index)}
                  disabled={isSubmitting || rules.length === 1}
                  aria-label={t('removeRule')}
                >
                  x
                </button>
              </div>
            ))}
          </div>
          {rulesError && <div className="form-helper form-helper--err">{rulesError}</div>}
        </div>

        <div className="form-actions">
          <button type="submit" className="btn btn-primary btn-lg" disabled={!canSubmit}>
            {isSubmitting ? t('creating') : t('createBtn')}
          </button>
          <button type="button" className="btn btn-secondary btn-lg" onClick={() => navigate(-1)} disabled={isSubmitting}>
            {t('cancel')}
          </button>
        </div>
      </form>
    </div>
  );
}
