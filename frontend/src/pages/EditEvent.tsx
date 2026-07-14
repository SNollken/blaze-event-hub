import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { ArrowRight, Ban, CircleStop, Radio, Save, ShieldCheck, Trophy } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  cancelEvent,
  finalizeEvent,
  getEvent,
  getEventParticipants,
  getEventStats,
  openEvent,
  updateEvent,
} from '../api/client';
import type { EventParticipantResponse, EventResponse, EventStatus } from '../api/types';
import { Modal } from '../components/Modal';
import { addToast, usePolling } from '../components/Toast';

type PendingAction = 'save' | 'open' | 'finalize' | 'cancel' | null;

const LIFECYCLE: Array<{ status: EventStatus; label: string }> = [
  { status: 'DRAFT', label: 'Rascunho' },
  { status: 'OPEN', label: 'Captura ao vivo' },
  { status: 'CLOSED', label: 'Pool fechado' },
  { status: 'COMPLETED', label: 'Sorteado' },
];

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

function formatDate(value: string | null): string {
  if (!value) return 'Não informado';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Não informado';
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

function statusLabel(status: EventStatus): string {
  if (status === 'FINALIZING') return 'Fechando entradas';
  return LIFECYCLE.find((item) => item.status === status)?.label || 'Cancelado';
}

function Lifecycle({ status }: { status: EventStatus }) {
  const lifecycleStatus = status === 'FINALIZING' ? 'OPEN' : status;
  const activeIndex = LIFECYCLE.findIndex((item) => item.status === lifecycleStatus);
  return (
    <ol className="lifecycle" aria-label="Ciclo de vida do giveaway">
      {LIFECYCLE.map((item, index) => {
        const stateClass = status === 'CANCELLED'
          ? 'is-muted'
          : index < activeIndex ? 'is-complete' : index === activeIndex ? 'is-current' : '';
        return (
          <li key={item.status} className={stateClass} aria-current={index === activeIndex ? 'step' : undefined}>
            <span>{index + 1}</span>
            <strong>{item.label}</strong>
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
  INACTIVE: { label: 'Inativa', description: 'A captura não está ativa para este evento.' },
  STARTING: { label: 'Iniciando', description: 'Aguardando a primeira sincronização confirmada com o chat.' },
  HEALTHY: { label: 'Saudável', description: 'A última sincronização com o chat foi concluída sem erro.' },
  DEGRADED: { label: 'Atenção', description: 'A captura encontrou uma falha e tentará sincronizar novamente.' },
  FINALIZING: { label: 'Finalizando', description: 'O limite de entrada foi fixado e a última sincronização está em andamento.' },
} as const;

function OpenEventPanel({ event, finalizing, onFinalize, onCancel, onEventUpdate }: OpenEventPanelProps) {
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
        <div className="section-label">Estado da captura</div>
        <div className="signal-command" role="status" aria-live="polite">
          <Radio aria-hidden="true" />
          <code>{event.entryCommand}</code>
        </div>
        <p><strong>{healthCopy.label}.</strong> {healthCopy.description} Cada conta Blaze entra uma única vez.</p>
        <dl className="event-stats">
          <div><dt>Participantes únicos</dt><dd>{participantCount}</dd></div>
          <div><dt>Aberto em</dt><dd>{formatDate(event.openedAt)}</dd></div>
          <div><dt>Última sincronização válida</dt><dd>{formatDate(stats.data?.lastSuccessfulPollAt || null)}</dd></div>
          {event.finalizationCutoffAt && <div><dt>Entradas aceitas até</dt><dd>{formatDate(event.finalizationCutoffAt)}</dd></div>}
        </dl>
        {stats.data?.lastErrorCode && (
          <div className="notice notice-danger" role="status">
            A sincronização precisa de atenção. Código: <code>{stats.data.lastErrorCode}</code>
          </div>
        )}
        {(stats.error || participants.error) && (
          <div className="notice notice-danger" role="alert">
            {stats.error || participants.error}
          </div>
        )}
        <div className="form-actions">
          <button
            type="button"
            className="btn btn-primary btn-lg"
            onClick={() => setConfirmFinalize(true)}
            disabled={!canFinalize || finalizationInProgress}
          >
            <CircleStop size={17} aria-hidden="true" />
            {finalizationInProgress ? 'Finalizando...' : 'Finalizar evento'}
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => void Promise.all([stats.reload(), participants.reload()])}
            disabled={stats.loading || participants.loading}
          >
            Atualizar agora
          </button>
          {event.status === 'OPEN' && (
            <button type="button" className="btn btn-danger" onClick={onCancel} disabled={finalizationInProgress}>
              <Ban size={16} aria-hidden="true" /> Cancelar evento
            </button>
          )}
        </div>
        {!stats.loading && participantCount === 0 && (
          <p className="form-helper">Aguarde ao menos uma entrada válida antes de finalizar.</p>
        )}
      </section>

      <section className="control-card">
        <div className="section-label">Entradas confirmadas</div>
        {participants.loading && !participants.data ? (
          <div className="empty" role="status">Sincronizando participantes...</div>
        ) : participants.data?.length ? (
          <ul className="participant-list" aria-live="polite">
            {participants.data.map((participant: EventParticipantResponse) => (
              <li key={participant.blazeUserId} className="participant-item">
                <span aria-hidden="true">{(participant.displayName || participant.blazeUsername || '?')[0].toUpperCase()}</span>
                <div>
                  <strong>{participant.displayName || participant.blazeUsername}</strong>
                  {participant.blazeUsername && <small>@{participant.blazeUsername}</small>}
                </div>
                <time dateTime={participant.enteredAt}>{formatDate(participant.enteredAt)}</time>
              </li>
            ))}
          </ul>
        ) : (
          <div className="empty">Nenhuma entrada válida até agora.</div>
        )}
      </section>

      <Modal
        open={confirmFinalize}
        onClose={() => setConfirmFinalize(false)}
        title="Congelar o pool de participantes?"
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmFinalize(false)} disabled={finalizationInProgress}>
              Continuar capturando
            </button>
            <button type="button" className="btn btn-danger" onClick={() => void confirm()} disabled={finalizationInProgress}>
              {finalizationInProgress ? 'Congelando...' : `Finalizar com ${participantCount} participantes`}
            </button>
          </>
        )}
      >
        <p><strong>Esta ação é irreversível.</strong> O hub fixará o limite de entrada, fará uma última sincronização e registrará o pool final.</p>
        <p>Depois disso, você poderá revisar o total e seguir para o sorteio.</p>
      </Modal>
    </div>
  );
}

export default function EditEvent() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [title, setTitle] = useState('');
  const [prize, setPrize] = useState('');
  const [description, setDescription] = useState('');
  const [entryCommand, setEntryCommand] = useState('!participar');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [error, setError] = useState('');

  const applyEvent = useCallback((loaded: EventResponse) => {
    setEvent(loaded);
    setTitle(loaded.title);
    setPrize(loaded.prize);
    setDescription(loaded.description || '');
    setEntryCommand(loaded.entryCommand || '!participar');
    setStartsAt(toDateTimeInput(loaded.startsAt));
    setEndsAt(toDateTimeInput(loaded.endsAt));
  }, []);

  useEffect(() => {
    let active = true;
    if (!id) {
      setError('Identificador de evento inválido.');
      setIsLoading(false);
      return () => {
        active = false;
      };
    }

    getEvent(id)
      .then((loaded) => {
        if (active) applyEvent(loaded);
      })
      .catch((loadError) => {
        if (active) setError(loadError instanceof Error ? loadError.message : 'Não foi possível carregar o giveaway.');
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [applyEvent, id]);

  const dateError = useMemo(() => {
    if (!startsAt || !endsAt) return '';
    return new Date(endsAt).getTime() <= new Date(startsAt).getTime()
      ? 'O encerramento precisa acontecer depois do início.'
      : '';
  }, [endsAt, startsAt]);

  const formError = () => {
    if (!title.trim()) return 'O título é obrigatório.';
    if (!prize.trim()) return 'O prêmio é obrigatório.';
    if (!/^![\p{L}\p{N}][\p{L}\p{N}_-]{0,78}$/u.test(entryCommand.trim())) {
      return 'Use ! seguido de letras, números, _ ou -.';
    }
    return dateError;
  };

  const saveDraft = async (): Promise<EventResponse | null> => {
    if (!id || event?.status !== 'DRAFT') return null;
    const invalid = formError();
    if (invalid) {
      setError(invalid);
      return null;
    }

    const updated = await updateEvent(id, {
      title: title.trim(),
      prize: prize.trim(),
      description: description.trim(),
      entryCommand: entryCommand.trim(),
      startsAt: toUpdateDate(startsAt),
      endsAt: toUpdateDate(endsAt),
    });
    applyEvent(updated);
    return updated;
  };

  const handleSave = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault();
    setPendingAction('save');
    setError('');
    try {
      const saved = await saveDraft();
      if (saved) addToast('success', 'Rascunho salvo.');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Não foi possível salvar o rascunho.');
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
      addToast('success', 'Captura aberta. O hub já está acompanhando o comando no chat.');
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : 'Não foi possível abrir a captura.');
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
        addToast('warning', 'Finalização iniciada. A última sincronização está em andamento.');
      } else {
        addToast('success', 'Pool registrado. O evento está pronto para o sorteio.');
      }
      return true;
    } catch (finalizeError) {
      setError(finalizeError instanceof Error ? finalizeError.message : 'Não foi possível finalizar o evento.');
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
      addToast('success', event?.status === 'OPEN' ? 'Evento aberto cancelado.' : 'Rascunho cancelado.');
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : 'Não foi possível cancelar o evento.');
    } finally {
      setPendingAction(null);
    }
  };

  if (isLoading) return <div className="empty" role="status">Carregando central do giveaway...</div>;

  if (!event) {
    return (
      <div className="hub-page">
        <div className="notice notice-danger" role="alert">{error || 'Giveaway não encontrado.'}</div>
        <button type="button" className="btn btn-secondary" onClick={() => navigate('/my-events')}>Voltar aos meus giveaways</button>
      </div>
    );
  }

  const busy = pendingAction !== null;

  return (
    <div className="hub-page">
      <header className="page-hero">
        <div>
          <span className={`pill pill--${event.status.toLowerCase()}`}>{statusLabel(event.status)}</span>
          <h1 className="page-title">{event.title}</h1>
          <p>Central de controle do giveaway</p>
        </div>
        <Link className="btn btn-secondary" to={`/events/${event.id}`}>Ver página pública</Link>
      </header>

      <Lifecycle status={event.status} />
      {error && <div className="notice notice-danger" role="alert">{error}</div>}

      {event.status === 'DRAFT' && (
        <form className="control-grid" onSubmit={handleSave} noValidate>
          <section className="control-card">
            <div className="section-label">Rascunho</div>
            <div className="form-group">
              <label htmlFor="manage-title">Título</label>
              <input id="manage-title" value={title} onChange={(change) => setTitle(change.target.value)} maxLength={140} disabled={busy} />
            </div>
            <div className="form-group">
              <label htmlFor="manage-prize">Prêmio</label>
              <input id="manage-prize" value={prize} onChange={(change) => setPrize(change.target.value)} maxLength={180} disabled={busy} />
            </div>
            <div className="form-group">
              <label htmlFor="manage-description">Descrição</label>
              <textarea id="manage-description" value={description} onChange={(change) => setDescription(change.target.value)} maxLength={2_000} disabled={busy} />
            </div>
          </section>

          <section className="control-card">
            <div className="section-label">Captura</div>
            <div className="form-group">
              <label htmlFor="manage-command">Comando exato do chat</label>
              <input
                id="manage-command"
                className="signal-command"
                value={entryCommand}
                onChange={(change) => setEntryCommand(change.target.value)}
                maxLength={80}
                autoComplete="off"
                spellCheck={false}
                disabled={busy}
              />
            </div>
            <div className="form-group">
              <label htmlFor="manage-channel">Canal vinculado</label>
              <input
                id="manage-channel"
                value={event.creatorChannelSlug ? `@${event.creatorChannelSlug}` : event.creatorChannelId}
                readOnly
                disabled
              />
              <span className="form-helper">O ID é o canal real resolvido pela Blaze e não pode ser trocado depois da criação.</span>
            </div>
          </section>

          <section className="control-card">
            <div className="section-label">Agenda opcional</div>
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="manage-starts-at">Início previsto</label>
                <input id="manage-starts-at" type="datetime-local" value={startsAt} onChange={(change) => setStartsAt(change.target.value)} disabled={busy} />
              </div>
              <div className="form-group">
                <label htmlFor="manage-ends-at">Encerramento previsto</label>
                <input id="manage-ends-at" type="datetime-local" value={endsAt} onChange={(change) => setEndsAt(change.target.value)} disabled={busy} />
              </div>
            </div>
            {dateError && <span className="form-helper form-helper--err" role="alert">{dateError}</span>}
          </section>

          <section className="control-card">
            <div className="section-label">Próximo passo</div>
            <p>Ao abrir, o título, o prêmio, o canal e o comando ficam travados para manter a captura consistente.</p>
            <div className="form-actions">
              <button type="submit" className="btn btn-secondary" disabled={busy}>
                <Save size={16} aria-hidden="true" />
                {pendingAction === 'save' ? 'Salvando...' : 'Salvar rascunho'}
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setConfirmOpen(true)} disabled={busy || Boolean(formError())}>
                <Radio size={16} aria-hidden="true" /> Abrir captura
              </button>
              <button type="button" className="btn btn-danger" onClick={() => setConfirmCancel(true)} disabled={busy}>
                <Ban size={16} aria-hidden="true" /> Cancelar evento
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
            <div className="section-label">Pool final registrado</div>
            <h2>{event.finalizedParticipantCount} participantes</h2>
            <p>Novas mensagens já não alteram o pool. O hash abaixo é o identificador técnico registrado para este conjunto.</p>
            <code className="signal-command">{event.finalizedPoolHash || 'Hash indisponível'}</code>
          </section>
          <section className="control-card">
            <div className="section-label">Pronto para sortear</div>
            <p>O sorteio será executado uma única vez e o resultado persistido ficará público.</p>
            <Link className="btn btn-primary btn-lg" to={`/events/${event.id}/draw`}>
              Ir para o sorteio <ArrowRight size={17} aria-hidden="true" />
            </Link>
          </section>
        </div>
      )}

      {event.status === 'COMPLETED' && (
        <section className="control-card">
          <Trophy size={32} aria-hidden="true" />
          <div className="section-label">Sorteio concluído</div>
          <h2>O resultado já está publicado</h2>
          <p>Consulte o vencedor e os dados técnicos registrados pelo sorteio.</p>
          <Link className="btn btn-primary" to={`/events/${event.id}/result`}>
            Ver resultado <ArrowRight size={17} aria-hidden="true" />
          </Link>
        </section>
      )}

      {event.status === 'CANCELLED' && (
        <section className="control-card">
          <Ban size={28} aria-hidden="true" />
          <div className="section-label">Evento cancelado</div>
          <h2>Este evento foi encerrado</h2>
          <p>Ele permanece disponível somente para consulta e não pode ser reaberto.</p>
          <Link className="btn btn-secondary" to="/my-events">Voltar aos meus giveaways</Link>
        </section>
      )}

      <Modal
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        title="Abrir a captura agora?"
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmOpen(false)} disabled={busy}>Ainda não</button>
            <button type="button" className="btn btn-primary" onClick={() => void handleOpen()} disabled={busy}>
              <Radio size={16} aria-hidden="true" /> {pendingAction === 'open' ? 'Abrindo...' : 'Abrir captura'}
            </button>
          </>
        )}
      >
        <p>O hub começará a aceitar <strong>{entryCommand}</strong> no canal vinculado. Os dados do evento ficarão travados durante a captura.</p>
      </Modal>

      <Modal
        open={confirmCancel}
        onClose={() => setConfirmCancel(false)}
        title={event.status === 'OPEN' ? 'Cancelar este evento aberto?' : 'Cancelar este rascunho?'}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmCancel(false)} disabled={busy}>
              {event.status === 'OPEN' ? 'Manter captura' : 'Manter rascunho'}
            </button>
            <button type="button" className="btn btn-danger" onClick={() => void handleCancel()} disabled={busy}>
              <Ban size={16} aria-hidden="true" /> {pendingAction === 'cancel' ? 'Cancelando...' : 'Cancelar evento'}
            </button>
          </>
        )}
      >
        <p>
          {event.status === 'OPEN'
            ? 'A captura será interrompida e as entradas deste evento não seguirão para sorteio. Esta ação não pode ser desfeita.'
            : 'O evento ficará somente para leitura e não poderá ser aberto depois.'}
        </p>
      </Modal>
    </div>
  );
}
