import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { createEvent, getChannels } from '../api/client';
import type { CreateRuleRequest } from '../api/types';

type ChannelInfo = {
  id: string;
  slug: string;
  displayName: string;
  avatarUrl: string | null;
};

type ActionType = 'vote' | 'sub' | 'gifted_sub';

type RuleDraft = {
  actionType: ActionType;
  thresholdAmount: number;
  entries: number;
};

type ChannelState = 'idle' | 'searching' | 'found' | 'not-found';

const ACTION_OPTIONS: Array<{ value: ActionType; label: string; unit: string }> = [
  { value: 'vote', label: 'Voto', unit: 'votos' },
  { value: 'sub', label: 'Sub', unit: 'subs' },
  { value: 'gifted_sub', label: 'Gifted sub', unit: 'gifted subs' },
];

const defaultRule = (): RuleDraft => ({
  actionType: 'vote',
  thresholdAmount: 50,
  entries: 1,
});

function normalizeSlug(value: string) {
  return value.trim().replace(/^@+/, '');
}

function positiveInteger(value: number) {
  return Number.isFinite(value) && Number.isInteger(value) && value > 0;
}

function toIsoDate(value: string) {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function actionUnit(actionType: ActionType) {
  return ACTION_OPTIONS.find((option) => option.value === actionType)?.unit ?? actionType;
}

function validateRules(rules: RuleDraft[]) {
  if (rules.length === 0) return 'Adicione pelo menos uma regra.';
  if (rules.some((rule) => !rule.actionType)) return 'Todas as regras precisam de um tipo de acao.';
  if (rules.some((rule) => !positiveInteger(rule.thresholdAmount))) {
    return 'Os marcos das regras precisam ser numeros inteiros maiores que zero.';
  }
  if (rules.some((rule) => !positiveInteger(rule.entries))) {
    return 'As entries das regras precisam ser numeros inteiros maiores que zero.';
  }
  return '';
}

export default function CreateEvent() {
  const navigate = useNavigate();
  const resolveToken = useRef(0);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [channelQuery, setChannelQuery] = useState('');
  const [channel, setChannel] = useState<ChannelInfo | null>(null);
  const [channelState, setChannelState] = useState<ChannelState>('idle');
  const [channelError, setChannelError] = useState('');
  const [rulesMode, setRulesMode] = useState('tier');
  const [maxEntriesPerParticipant, setMaxEntriesPerParticipant] = useState(0);
  const [requiresInterestBeforeAction, setRequiresInterestBeforeAction] = useState(false);
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [rules, setRules] = useState<RuleDraft[]>([defaultRule()]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const slug = useMemo(() => normalizeSlug(channelQuery), [channelQuery]);

  useEffect(() => {
    const token = resolveToken.current + 1;
    resolveToken.current = token;

    if (!slug) {
      setChannel(null);
      setChannelState('idle');
      setChannelError('');
      return;
    }

    setChannel(null);
    setChannelState('searching');
    setChannelError('');

    const timeout = window.setTimeout(async () => {
      try {
        const resolved = await getChannels(slug);
        if (resolveToken.current !== token) return;
        setChannel(resolved);
        setChannelState('found');
      } catch {
        if (resolveToken.current !== token) return;
        setChannel(null);
        setChannelState('not-found');
        setChannelError('Canal nao encontrado.');
      }
    }, 500);

    return () => window.clearTimeout(timeout);
  }, [slug]);

  const rulesError = useMemo(() => validateRules(rules), [rules]);
  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? 'A data final precisa ser posterior a data inicial.'
      : '';
  }, [startsAt, endsAt]);

  const canSubmit = Boolean(title.trim())
    && Boolean(channel)
    && !rulesError
    && !dateError
    && channelState !== 'searching'
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
    if (!title.trim()) return 'Defina um titulo para o evento.';
    if (!slug) return 'Informe o nome do canal.';
    if (!channel) return channelState === 'searching' ? 'Aguarde a validacao do canal.' : 'Selecione um canal valido.';
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

    const payload = {
      title: title.trim(),
      description: description.trim() || undefined,
      creatorChannelId: channel!.id,
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
      setError(err instanceof Error ? err.message : 'Erro ao criar evento.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const channelFieldClass = [
    'form-field',
    channelState === 'found' ? 'form-field--ok' : '',
    channelState === 'not-found' ? 'form-field--err' : '',
  ].filter(Boolean).join(' ');

  return (
    <div style={{ maxWidth: 760 }}>
      <h1 className="page-title">Criar evento</h1>
      <p className="page-subtitle">Configure o canal monitorado e as regras de entries.</p>

      {error && <div className="toast toast-error" style={{ position: 'static', marginBottom: 24 }}>{error}</div>}

      <form onSubmit={handleSubmit} autoComplete="off">
        <div className="form-section">
          <label className="form-label" htmlFor="event-title">Titulo</label>
          <div className="form-field">
            <input
              id="event-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Ex: Giveaway 50 votos"
              disabled={isSubmitting}
              required
            />
          </div>
        </div>

        <div className="form-section">
          <label className="form-label" htmlFor="event-description">Descricao</label>
          <div className="form-field">
            <textarea
              id="event-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Descreva as regras do evento para os participantes"
              disabled={isSubmitting}
            />
          </div>
        </div>

        <div className="form-section">
          <label className="form-label" htmlFor="event-channel">Canal Blaze</label>
          <div className={channelFieldClass}>
            <input
              id="event-channel"
              value={channelQuery}
              onChange={(event) => setChannelQuery(event.target.value)}
              placeholder="Nome do canal"
              autoComplete="off"
              spellCheck={false}
              disabled={isSubmitting}
            />
            {channelState === 'searching' && (
              <span className="search-dots" style={{ position: 'absolute', right: 16, top: 18 }}>
                <span />
                <span />
                <span />
              </span>
            )}
          </div>

          {channelError && <div className="form-helper form-helper--err">{channelError}</div>}

          {channel && (
            <div className="channel-preview">
              {channel.avatarUrl ? (
                <img src={channel.avatarUrl} alt="" />
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
                  {(channel.displayName || channel.slug || '?')[0].toUpperCase()}
                </div>
              )}
              <div>
                <div className="ch-name">{channel.displayName || channel.slug}</div>
                <div className="ch-meta">
                  @{channel.slug}
                  <span style={{ opacity: 0.35 }}>.</span>
                  <span className="ch-id">{channel.id}</span>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="form-section">
          <div className="form-row">
            <div>
              <label className="form-label" htmlFor="event-rules-mode">Modo das regras</label>
              <div className="form-field">
                <select
                  id="event-rules-mode"
                  value={rulesMode}
                  onChange={(event) => setRulesMode(event.target.value)}
                  disabled={isSubmitting}
                >
                  <option value="tier">Tier - maior marco</option>
                  <option value="cumulative">Acumulativo</option>
                </select>
              </div>
            </div>
            <div>
              <label className="form-label" htmlFor="event-max-entries">Max entries/pessoa</label>
              <div className="form-field">
                <input
                  id="event-max-entries"
                  type="number"
                  min={0}
                  value={maxEntriesPerParticipant || ''}
                  onChange={(event) => setMaxEntriesPerParticipant(Number(event.target.value) || 0)}
                  placeholder="0 = ilimitado"
                  disabled={isSubmitting}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="form-section">
          <div className="form-row">
            <div>
              <label className="form-label" htmlFor="event-starts-at">Inicio</label>
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
              <label className="form-label" htmlFor="event-ends-at">Fim</label>
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
            Exigir interesse antes da acao
          </label>
        </div>

        <div className="form-section">
          <div className="section-header" style={{ marginBottom: 12 }}>
            <label className="form-label" style={{ margin: 0 }}>Regras de entries</label>
            <button type="button" className="btn-add-rule" onClick={addRule} disabled={isSubmitting}>
              + Adicionar regra
            </button>
          </div>

          <div className="flex flex-col gap-sm">
            {rules.map((rule, index) => (
              <div className="rule-card" key={`${rule.actionType}-${index}`}>
                <span className="r-sep">A cada</span>
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
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                <span className="r-sep">=</span>
                <input
                  className="r-input"
                  type="number"
                  min={1}
                  value={rule.entries || ''}
                  onChange={(event) => updateRule(index, 'entries', Number(event.target.value) || 0)}
                  disabled={isSubmitting}
                />
                <span className="r-sep">{rule.entries === 1 ? 'entry' : 'entries'} por {actionUnit(rule.actionType)}</span>
                <button
                  type="button"
                  className="r-close"
                  onClick={() => removeRule(index)}
                  disabled={isSubmitting || rules.length === 1}
                  aria-label="Remover regra"
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
            {isSubmitting ? 'Criando...' : 'Criar evento'}
          </button>
          <button type="button" className="btn btn-secondary btn-lg" onClick={() => navigate(-1)} disabled={isSubmitting}>
            Cancelar
          </button>
        </div>
      </form>
    </div>
  );
}
