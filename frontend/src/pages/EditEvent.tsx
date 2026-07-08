import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  addEventRule,
  getEvent,
  removeEventRule,
  updateEvent,
  updateEventRule,
} from '../api/client';
import type { CreateRuleRequest, EventResponse, RuleResponse, UpdateRuleRequest } from '../api/types';

type ActionType = 'vote' | 'sub' | 'gifted_sub';

type RuleDraft = {
  clientKey: string;
  id?: string;
  actionType: ActionType;
  thresholdAmount: number;
  entries: number;
  isActive?: boolean;
};

const ACTION_OPTIONS: Array<{ value: ActionType; label: string; unit: string }> = [
  { value: 'vote', label: 'Voto', unit: 'votos' },
  { value: 'sub', label: 'Sub', unit: 'subs' },
  { value: 'gifted_sub', label: 'Gifted sub', unit: 'gifted subs' },
];

function newRuleKey() {
  return `new-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function normalizeActionType(value: string | undefined): ActionType {
  if (value === 'sub' || value === 'gifted_sub') return value;
  return 'vote';
}

function positiveInteger(value: number) {
  return Number.isFinite(value) && Number.isInteger(value) && value > 0;
}

function toDateTimeInput(value: string | null | undefined) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function toIsoDate(value: string) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString();
}

function fromRuleResponse(rule: RuleResponse): RuleDraft {
  return {
    clientKey: rule.id,
    id: rule.id,
    actionType: normalizeActionType(rule.actionType),
    thresholdAmount: rule.thresholdAmount,
    entries: rule.entries,
    isActive: rule.isActive,
  };
}

function toCreateRule(rule: RuleDraft): CreateRuleRequest {
  return {
    actionType: rule.actionType,
    thresholdAmount: Math.trunc(rule.thresholdAmount),
    entries: Math.trunc(rule.entries),
  };
}

function toUpdateRule(rule: RuleDraft): UpdateRuleRequest {
  return {
    ...toCreateRule(rule),
    isActive: rule.isActive ?? true,
  };
}

function hasRuleChanged(original: RuleDraft, current: RuleDraft) {
  return original.actionType !== current.actionType
    || original.thresholdAmount !== current.thresholdAmount
    || original.entries !== current.entries
    || (original.isActive ?? true) !== (current.isActive ?? true);
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

export default function EditEvent() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const originalRulesRef = useRef<RuleDraft[]>([]);

  const [event, setEvent] = useState<EventResponse | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [rulesMode, setRulesMode] = useState('tier');
  const [maxEntriesPerParticipant, setMaxEntriesPerParticipant] = useState(0);
  const [requiresInterestBeforeAction, setRequiresInterestBeforeAction] = useState(false);
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [rules, setRules] = useState<RuleDraft[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let isActive = true;

    async function loadEvent() {
      if (!id) {
        setError('Evento invalido.');
        setIsLoading(false);
        return;
      }

      setIsLoading(true);
      setError('');

      try {
        const loaded = await getEvent(id);
        if (!isActive) return;

        const loadedRules = (loaded.rules || []).map(fromRuleResponse);
        originalRulesRef.current = loadedRules;
        setEvent(loaded);
        setTitle(loaded.title || '');
        setDescription(loaded.description || '');
        setRulesMode(loaded.rulesMode || loaded.mode || 'tier');
        setMaxEntriesPerParticipant(loaded.maxEntriesPerParticipant ?? loaded.maxEntries ?? 0);
        setRequiresInterestBeforeAction(Boolean(loaded.requiresInterestBeforeAction));
        setStartsAt(toDateTimeInput(loaded.startsAt));
        setEndsAt(toDateTimeInput(loaded.endsAt));
        setRules(loadedRules.length > 0 ? loadedRules : [{
          clientKey: newRuleKey(),
          actionType: 'vote',
          thresholdAmount: 50,
          entries: 1,
          isActive: true,
        }]);
      } catch (err) {
        if (!isActive) return;
        setError(err instanceof Error ? err.message : 'Erro ao carregar evento.');
      } finally {
        if (isActive) setIsLoading(false);
      }
    }

    loadEvent();

    return () => {
      isActive = false;
    };
  }, [id]);

  const isDraft = event?.status === 'DRAFT';
  const disabled = isSaving || !isDraft;
  const channelLabel = event?.channelSlug || event?.creatorChannelId || 'Canal nao informado';
  const rulesError = useMemo(() => validateRules(rules), [rules]);
  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? 'A data final precisa ser posterior a data inicial.'
      : '';
  }, [startsAt, endsAt]);

  const canSave = Boolean(id)
    && Boolean(title.trim())
    && isDraft
    && !rulesError
    && !dateError
    && !isSaving;

  const updateRule = <K extends keyof Omit<RuleDraft, 'clientKey' | 'id'>>(
    index: number,
    field: K,
    value: RuleDraft[K],
  ) => {
    setRules((current) => current.map((rule, ruleIndex) => (
      ruleIndex === index ? { ...rule, [field]: value } : rule
    )));
  };

  const addRule = () => {
    setRules((current) => [...current, {
      clientKey: newRuleKey(),
      actionType: 'vote',
      thresholdAmount: 100,
      entries: 3,
      isActive: true,
    }]);
  };

  const removeRule = (index: number) => {
    setRules((current) => (current.length === 1 ? current : current.filter((_, ruleIndex) => ruleIndex !== index)));
  };

  const validateForm = () => {
    if (!isDraft) return 'Apenas eventos em DRAFT podem ser editados.';
    if (!title.trim()) return 'Defina um titulo para o evento.';
    if (rulesError) return rulesError;
    if (dateError) return dateError;
    return '';
  };

  const syncRules = async (eventId: string) => {
    const originalById = new Map(
      originalRulesRef.current
        .filter((rule): rule is RuleDraft & { id: string } => Boolean(rule.id))
        .map((rule) => [rule.id, rule]),
    );
    const currentIds = new Set(rules.map((rule) => rule.id).filter(Boolean));

    for (const rule of rules) {
      if (!rule.id) continue;
      const original = originalById.get(rule.id);
      if (original && hasRuleChanged(original, rule)) {
        await updateEventRule(eventId, rule.id, toUpdateRule(rule));
      }
    }

    for (const rule of rules) {
      if (!rule.id) {
        await addEventRule(eventId, toCreateRule(rule));
      }
    }

    for (const original of originalRulesRef.current) {
      if (original.id && !currentIds.has(original.id)) {
        await removeEventRule(eventId, original.id);
      }
    }
  };

  const handleSubmit = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault();
    if (!id) return;

    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    setIsSaving(true);
    setError('');

    try {
      await updateEvent(id, {
        title: title.trim(),
        description,
        rulesMode,
        maxEntriesPerParticipant: Math.max(0, Math.trunc(maxEntriesPerParticipant || 0)),
        requiresInterestBeforeAction,
        startsAt: toIsoDate(startsAt),
        endsAt: toIsoDate(endsAt),
      });

      await syncRules(id);
      navigate(`/events/${id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao salvar evento.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <div className="empty">Carregando...</div>;
  }

  if (!event && error) {
    return (
      <div style={{ maxWidth: 760 }}>
        <h1 className="page-title">Editar evento</h1>
        <div className="toast toast-error" style={{ position: 'static', marginBottom: 24 }}>{error}</div>
        <button type="button" className="btn btn-secondary" onClick={() => navigate(-1)}>Voltar</button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 760 }}>
      <h1 className="page-title">Editar evento</h1>
      <p className="page-subtitle">Atualize os dados principais e as regras do evento.</p>

      {!isDraft && (
        <div className="toast toast-warning" style={{ position: 'static', marginBottom: 24 }}>
          Evento com status {event?.status}. Edicao disponivel somente para DRAFT.
        </div>
      )}

      {error && <div className="toast toast-error" style={{ position: 'static', marginBottom: 24 }}>{error}</div>}

      <form onSubmit={handleSubmit} autoComplete="off">
        <div className="form-section">
          <label className="form-label" htmlFor="event-title">Titulo</label>
          <div className="form-field">
            <input
              id="event-title"
              value={title}
              onChange={(changeEvent) => setTitle(changeEvent.target.value)}
              placeholder="Ex: Giveaway 50 votos"
              disabled={disabled}
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
              onChange={(changeEvent) => setDescription(changeEvent.target.value)}
              placeholder="Descreva as regras do evento para os participantes"
              disabled={disabled}
            />
          </div>
        </div>

        <div className="form-section">
          <label className="form-label" htmlFor="event-channel">Canal Blaze</label>
          <div className="form-field form-field--ok">
            <input id="event-channel" value={channelLabel} readOnly disabled />
          </div>
          <div className="channel-preview">
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
              {channelLabel[0]?.toUpperCase() || '?'}
            </div>
            <div>
              <div className="ch-name">{channelLabel}</div>
              <div className="ch-meta">
                Readonly
                <span style={{ opacity: 0.35 }}>.</span>
                <span className="ch-id">{event?.creatorChannelId}</span>
              </div>
            </div>
          </div>
          <div className="form-helper">O canal nao pode ser alterado apos a criacao.</div>
        </div>

        <div className="form-section">
          <div className="form-row">
            <div>
              <label className="form-label" htmlFor="event-rules-mode">Modo das regras</label>
              <div className="form-field">
                <select
                  id="event-rules-mode"
                  value={rulesMode}
                  onChange={(changeEvent) => setRulesMode(changeEvent.target.value)}
                  disabled={disabled}
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
                  onChange={(changeEvent) => setMaxEntriesPerParticipant(Number(changeEvent.target.value) || 0)}
                  placeholder="0 = ilimitado"
                  disabled={disabled}
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
                  onChange={(changeEvent) => setStartsAt(changeEvent.target.value)}
                  disabled={disabled}
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
                  onChange={(changeEvent) => setEndsAt(changeEvent.target.value)}
                  disabled={disabled}
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
              onChange={(changeEvent) => setRequiresInterestBeforeAction(changeEvent.target.checked)}
              disabled={disabled}
            />
            Exigir interesse antes da acao
          </label>
        </div>

        <div className="form-section">
          <div className="section-header" style={{ marginBottom: 12 }}>
            <label className="form-label" style={{ margin: 0 }}>Regras de entries</label>
            <button type="button" className="btn-add-rule" onClick={addRule} disabled={disabled}>
              + Adicionar regra
            </button>
          </div>

          <div className="flex flex-col gap-sm">
            {rules.map((rule, index) => (
              <div className="rule-card" key={rule.clientKey}>
                <span className="r-sep">A cada</span>
                <input
                  className="r-input"
                  type="number"
                  min={1}
                  value={rule.thresholdAmount || ''}
                  onChange={(changeEvent) => updateRule(index, 'thresholdAmount', Number(changeEvent.target.value) || 0)}
                  disabled={disabled}
                />
                <select
                  className="r-input"
                  style={{ width: 132 }}
                  value={rule.actionType}
                  onChange={(changeEvent) => updateRule(index, 'actionType', changeEvent.target.value as ActionType)}
                  disabled={disabled}
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
                  onChange={(changeEvent) => updateRule(index, 'entries', Number(changeEvent.target.value) || 0)}
                  disabled={disabled}
                />
                <span className="r-sep">{rule.entries === 1 ? 'entry' : 'entries'} por {actionUnit(rule.actionType)}</span>
                <button
                  type="button"
                  className="r-close"
                  onClick={() => removeRule(index)}
                  disabled={disabled || rules.length === 1}
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
          <button type="submit" className="btn btn-primary btn-lg" disabled={!canSave}>
            {isSaving ? 'Salvando...' : 'Salvar alteracoes'}
          </button>
          <button type="button" className="btn btn-secondary btn-lg" onClick={() => navigate(`/events/${id}`)} disabled={isSaving}>
            Cancelar
          </button>
        </div>
      </form>
    </div>
  );
}
