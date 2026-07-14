import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ApiError,
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

type DrawPhase = 'idle' | 'rolling' | 'done';

const dateFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const numberFormatter = new Intl.NumberFormat('pt-BR');
const ROLL_INTERVAL_MS = 80;
const MINIMUM_ROLL_MS = 1400;

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Data indisponível' : dateFormatter.format(date);
}

function participantName(participant: EventParticipantResponse) {
  return participant.displayName || participant.blazeUsername || participant.blazeUserId;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return 'Apenas o criador autenticado deste giveaway pode acessar o sorteio.';
  }
  return error instanceof Error && error.message ? error.message : fallback;
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

    async function loadDraw(eventId: string) {
      setLoading(true);
      setError('');
      setResult(null);
      setPhase('idle');

      try {
        const session = await getOAuthSession();
        if (!session.connected) {
          throw new Error('Conecte sua conta Blaze para acessar o sorteio.');
        }

        const [loadedCreator, loadedEvent, loadedStats, loadedParticipants] = await Promise.all([
          getMe(),
          getEvent(eventId),
          getEventStats(eventId),
          getEventParticipants(eventId),
        ]);
        if (!active) return;

        setCreator(loadedCreator);
        setEvent(loadedEvent);
        setStats(loadedStats);
        setParticipants(loadedParticipants);

        if (loadedEvent.status === 'COMPLETED') {
          const existingResult = await getEventResult(eventId);
          if (!active) return;
          setResult(existingResult);
          setDisplayName(existingResult.winnerDisplayName || existingResult.winnerUsername || 'Vencedor Blaze');
          setPhase('done');
        }
      } catch (loadError) {
        if (active) {
          setError(getErrorMessage(loadError, 'Não foi possível preparar este sorteio.'));
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    if (id) {
      void loadDraw(id);
    } else {
      setError('O identificador do giveaway não foi informado.');
      setLoading(false);
    }

    return () => {
      active = false;
    };
  }, [id]);

  const sortedParticipants = useMemo(
    () => [...participants].sort((first, second) => (
      participantName(first).localeCompare(participantName(second), 'pt-BR', { sensitivity: 'base' })
    )),
    [participants],
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
      ? 'Não há participantes no pool finalizado.'
      : !poolMatchesSnapshot
        ? 'A lista recebida não corresponde ao snapshot final. O sorteio foi bloqueado por segurança.'
        : null
    : null;
  const drawAvailabilityMessage = loading
    ? 'Conferindo evento e participantes…'
    : event && event.status !== 'CLOSED'
      ? 'Finalize as entradas antes de sortear.'
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
    setDisplayName(reduceMotion ? 'Validando pool no servidor…' : names[0] || 'Sorteando…');

    if (!reduceMotion) {
      let cursor = 0;
      spinRef.current = window.setInterval(() => {
        cursor = (cursor + 1) % names.length;
        setDisplayName(names[cursor] || 'Sorteando…');
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
      setDisplayName(drawResult.winnerDisplayName || drawResult.winnerUsername || 'Vencedor Blaze');
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
      setError(getErrorMessage(drawError, 'O servidor não conseguiu concluir o sorteio. Tente novamente.'));
    }
  }, [canDraw, id, participants, stopRolling]);

  const stageLabel = phase === 'rolling'
    ? displayName || 'Sorteando…'
    : result
      ? result.winnerDisplayName || result.winnerUsername
      : loading
        ? 'Preparando pool…'
        : 'Pronto para sortear';

  const winnerName = result?.winnerDisplayName || result?.winnerUsername || '';

  return (
    <div className="hub-page draw-page">
      <header className="page-hero">
        <div className="page-hero-copy">
          <span className="page-eyebrow">Área do criador</span>
          <h1 className="page-title">Sorteio ao vivo</h1>
          <p className="page-subtitle">
            O pool finalizado é uniforme: cada participante Blaze tem exatamente uma chance.
          </p>
        </div>
        {creator && <span className="creator-badge">Conectado como {creator.displayName}</span>}
      </header>

      {error && <div className="notice notice-danger" role="alert">{error}</div>}

      {event && (
        <section className="control-card draw-summary" aria-labelledby="draw-event-title">
          <div>
            <span className="section-label">Giveaway finalizado</span>
            <h2 id="draw-event-title">{event.title}</h2>
            <p>{event.prize}</p>
          </div>
          <div className="draw-pool-count">
            <strong>{numberFormatter.format(expectedPoolCount)}</strong>
            <span>{expectedPoolCount === 1 ? 'participante' : 'participantes'}</span>
          </div>
        </section>
      )}

      <section aria-labelledby="draw-stage-title">
        <h2 id="draw-stage-title" className="sr-only">Palco do sorteio</h2>
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
            ? 'Sorteio em andamento.'
            : phase === 'done'
              ? `Sorteio concluído. ${winnerName} venceu.`
              : ''}
        </p>
      </section>

      {!result && (
        <div className="draw-controls">
          <button
            type="button"
            className="btn btn-primary btn-lg"
            disabled={!canDraw}
            aria-describedby={drawAvailabilityMessageId}
            onClick={() => setConfirmOpen(true)}
          >
            {phase === 'rolling' ? 'Sorteando no servidor…' : 'Iniciar sorteio'}
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
        title="Confirmar sorteio"
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmOpen(false)}>
              Voltar
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setConfirmOpen(false);
                void runDraw();
              }}
            >
              Confirmar e sortear
            </button>
          </>
        )}
      >
        <p>
          O servidor escolherá uma pessoa entre {numberFormatter.format(expectedPoolCount)} participantes,
          todos com a mesma chance. O vencedor será persistido uma única vez e o resultado ficará público.
        </p>
      </Modal>

      {result && (
        <section className="winner-card" aria-labelledby="draw-winner-title">
          <div className="winner-avatar" aria-hidden="true">{winnerName.slice(0, 1).toUpperCase()}</div>
          <h2 id="draw-winner-title" className="winner-name">{winnerName}</h2>
          <p className="winner-meta">
            {result.winnerUsername ? `@${result.winnerUsername} · ` : ''}sorteado em {formatDate(result.selectedAt)}
          </p>
          <dl className="proof-grid proof-grid-compact">
            <div><dt>Método</dt><dd>{result.drawMethod}</dd></div>
            <div><dt>Participantes</dt><dd>{numberFormatter.format(result.participantCount)}</dd></div>
          </dl>
          <Link className="btn btn-secondary" to={`/events/${result.eventId}/result`}>
            Abrir resultado público
          </Link>
        </section>
      )}

      <section className="control-card participant-pool" aria-labelledby="participant-pool-title">
        <div className="section-heading">
          <div>
            <span className="section-label">Pool congelado</span>
            <h2 id="participant-pool-title">Participantes Blaze</h2>
          </div>
          <span className="uniform-badge">1 pessoa = 1 chance</span>
        </div>
        {sortedParticipants.length === 0 ? (
          <div className="empty">Nenhum participante disponível.</div>
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
                  <span className="dpi-entries" aria-label="Uma chance">1×</span>
                </li>
              );
            })}
          </ol>
        )}
      </section>
    </div>
  );
}
