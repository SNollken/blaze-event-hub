import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  getEvent,
  getEventResult,
  getEventStats,
  type EventLifecycleStats,
  type EventResponse,
  type EventResultResponse,
} from '../api/client';
import { usePolling } from '../components/Toast';

const dateFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const numberFormatter = new Intl.NumberFormat('pt-BR');

const STATUS_LABELS: Record<EventResponse['status'], string> = {
  DRAFT: 'Rascunho',
  OPEN: 'Capturando entradas',
  FINALIZING: 'Finalizando entradas',
  CLOSED: 'Entradas finalizadas',
  COMPLETED: 'Sorteio concluído',
  CANCELLED: 'Cancelado',
};

const STATUS_CLASSES: Record<EventResponse['status'], string> = {
  DRAFT: 'pill--draft',
  OPEN: 'pill--open',
  FINALIZING: 'pill--finalizing',
  CLOSED: 'pill--closed',
  COMPLETED: 'pill--completed',
  CANCELLED: 'pill--cancelled',
};

type LifecycleKey = 'created' | 'opened' | 'closed' | 'completed';

interface LifecycleStep {
  key: LifecycleKey;
  title: string;
  description: string;
  timestamp: string | null;
}

function formatDate(value: string | null | undefined) {
  if (!value) return 'Ainda não ocorreu';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Data indisponível' : dateFormatter.format(date);
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function lifecycleState(event: EventResponse, step: LifecycleKey) {
  if (event.status === 'CANCELLED') return step === 'created' ? 'is-complete' : 'is-cancelled';

  const order: LifecycleKey[] = ['created', 'opened', 'closed', 'completed'];
  const currentByStatus: Record<Exclude<EventResponse['status'], 'CANCELLED'>, LifecycleKey> = {
    DRAFT: 'created',
    OPEN: 'opened',
    FINALIZING: 'opened',
    CLOSED: 'closed',
    COMPLETED: 'completed',
  };
  const current = currentByStatus[event.status];
  const stepIndex = order.indexOf(step);
  const currentIndex = order.indexOf(current);
  if (stepIndex < currentIndex) return 'is-complete';
  if (stepIndex === currentIndex) return 'is-current';
  return 'is-pending';
}

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const [result, setResult] = useState<EventResultResponse | null>(null);
  const [resultNotice, setResultNotice] = useState('');

  const fetchDetail = useCallback(async () => {
    if (!id) throw new Error('O identificador do giveaway não foi informado.');
    const loadedEvent = await getEvent(id);
    try {
      return { event: loadedEvent, stats: await getEventStats(id), statsError: '' };
    } catch {
      return { event: loadedEvent, stats: null, statsError: 'Contagem indisponível' };
    }
  }, [id]);
  const detail = usePolling(fetchDetail, 10_000);
  const currentDetail = detail.data?.event.id === id ? detail.data : null;
  const event = currentDetail?.event || null;
  const stats: EventLifecycleStats | null = currentDetail?.stats || null;
  const statsError = currentDetail?.statsError || '';
  const loading = !currentDetail && !detail.error;
  const error = detail.error || '';

  useEffect(() => {
    let active = true;
    setResultNotice('');
    if (!id || event?.status !== 'COMPLETED') {
      setResult(null);
      return () => { active = false; };
    }

    getEventResult(id)
      .then((loadedResult) => {
        if (active) setResult(loadedResult);
      })
      .catch((resultError) => {
        if (!active) return;
        setResult(null);
        setResultNotice(getErrorMessage(
          resultError,
          'O sorteio foi concluído, mas a publicação do resultado ainda não está disponível.',
        ));
      });

    return () => {
      active = false;
    };
  }, [event?.status, id]);

  useEffect(() => {
    if (!event) return;
    document.title = `${event.title} | Blaze Event Hub`;
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute(
      'content',
      `${event.title}: acompanhe o comando, o estado e o resultado deste giveaway.`,
    );
  }, [event]);

  const lifecycle = useMemo<LifecycleStep[]>(() => event ? [
    {
      key: 'created',
      title: 'Evento registrado',
      description: 'O criador preparou o giveaway e definiu o prêmio.',
      timestamp: event.createdAt,
    },
    {
      key: 'opened',
      title: 'Captura iniciada',
      description: `Mensagens com ${event.entryCommand} passaram a valer como entrada.`,
      timestamp: event.openedAt,
    },
    {
      key: 'closed',
      title: 'Entradas finalizadas',
      description: 'O pool foi congelado e novas mensagens não alteram o sorteio.',
      timestamp: event.closedAt,
    },
    {
      key: 'completed',
      title: 'Vencedor sorteado',
      description: 'O servidor persistiu um único resultado para este evento.',
      timestamp: event.completedAt,
    },
  ] : [], [event]);

  if (loading) {
    return <div className="hub-page"><div className="empty" role="status">Carregando giveaway…</div></div>;
  }

  if (!event) {
    return (
      <div className="hub-page">
        <div className="empty-state" role="alert">
          <h1 className="empty-state-title">Giveaway indisponível</h1>
          <p className="empty-state-desc">{error || 'Este giveaway não foi encontrado.'}</p>
          <div className="page-actions">
            <button type="button" className="btn btn-secondary" onClick={() => void detail.reload()}>Tentar novamente</button>
            <Link className="btn btn-ghost" to="/events">Ver giveaways</Link>
          </div>
        </div>
      </div>
    );
  }

  const participantCount = stats
    ? (event.status === 'DRAFT' || event.status === 'OPEN' || event.status === 'FINALIZING'
      ? stats.participantCount
      : stats.finalizedParticipantCount)
    : event.status === 'CLOSED' || event.status === 'COMPLETED'
      ? event.finalizedParticipantCount
      : null;

  return (
    <div className="hub-page event-detail-page">
      <header className="page-hero">
        <div className="page-hero-copy">
          <span className="page-eyebrow">Giveaway na Blaze.stream</span>
          <h1 className="page-title">{event.title}</h1>
          {event.description && <p className="page-subtitle">{event.description}</p>}
        </div>
        <div className="page-actions">
          {event.creatorChannelSlug && (
            <a
              className="btn btn-primary"
              href={`https://blaze.stream/${encodeURIComponent(event.creatorChannelSlug)}`}
              target="_blank"
              rel="noreferrer"
            >
              Abrir transmissão <span aria-hidden="true">↗</span>
            </a>
          )}
          <span className={`pill ${STATUS_CLASSES[event.status]}`}>{STATUS_LABELS[event.status]}</span>
        </div>
      </header>

      {error && (
        <div className="notice notice--warning" role="status">
          Não foi possível atualizar esta página agora. Os últimos dados recebidos continuam visíveis.
        </div>
      )}

      {event.status === 'CANCELLED' && (
        <div className="notice notice-danger" role="status">
          Este giveaway foi cancelado. Nenhuma entrada será sorteada.
        </div>
      )}

      {event.status === 'FINALIZING' && (
        <div className="notice notice--warning" role="status">
          O limite de entrada já foi fixado. O Hub está concluindo a última sincronização antes de registrar o pool final.
        </div>
      )}

      {statsError && (
        <div className="notice notice-danger" role="status">
          <strong>{statsError}</strong>. Não foi possível atualizar as métricas deste giveaway agora.
        </div>
      )}

      <div className="event-detail-grid">
        <section className="control-card prize-card" aria-labelledby="prize-title">
          <span className="section-label">Prêmio</span>
          <h2 id="prize-title">{event.prize}</h2>
          {event.creatorChannelSlug && (
            <p className="channel-identity">
              Por <strong>{event.creatorChannelDisplayName || event.creatorChannelSlug}</strong>
              {' '}@{event.creatorChannelSlug}
            </p>
          )}
          <p>O criador é responsável pela entrega e pelas condições divulgadas durante a transmissão.</p>
        </section>

        <section className="control-card command-card" aria-labelledby="command-title">
          <span className="section-label">Como entrar</span>
          <h2 id="command-title">Envie este comando no chat</h2>
          <code className={`signal-command${event.status === 'OPEN' ? ' is-live' : ''}`}>
            {event.entryCommand}
          </code>
          <p>
            {event.status === 'OPEN'
              ? stats?.captureHealth === 'HEALTHY'
                ? 'A captura está sincronizada. Cada usuário Blaze entra uma única vez.'
                : stats?.captureHealth === 'DEGRADED'
                  ? 'O evento continua aberto, mas a sincronização está com atenção no momento.'
                  : 'O evento está aberto e aguarda a próxima sincronização confirmada.'
              : event.status === 'FINALIZING'
                ? 'O limite de entrada foi fixado e a última sincronização está em andamento.'
              : event.status === 'DRAFT'
                ? 'A captura começará quando o criador abrir o evento.'
                : 'A captura terminou e o pool de participantes está congelado.'}
          </p>
        </section>
      </div>

      <section className="metrics-row" aria-label="Resumo do giveaway">
        <div className="metric">
          <strong className="metric-val">
            {participantCount === null
              ? 'Indisponível'
              : `${numberFormatter.format(participantCount)} ${participantCount === 1 ? 'participante' : 'participantes'}`}
          </strong>
          <span className="metric-lbl">no pool atual</span>
        </div>
        <div className="metric">
          <strong className="metric-val">1</strong>
          <span className="metric-lbl">chance por usuário</span>
        </div>
        <div className="metric">
          <strong className="metric-val">
            {event.status === 'OPEN' ? 'Aberto' : event.status === 'FINALIZING' ? 'Fechando' : 'Travado'}
          </strong>
          <span className="metric-lbl">estado do pool</span>
        </div>
      </section>

      <section className="control-card lifecycle-card" aria-labelledby="lifecycle-title">
        <div className="section-heading">
          <div>
            <span className="section-label">Linha do tempo</span>
            <h2 id="lifecycle-title">Ciclo do giveaway</h2>
          </div>
        </div>
        <ol className="lifecycle">
          {lifecycle.map((step) => (
            <li key={step.key} className={`lifecycle-step ${lifecycleState(event, step.key)}`}>
              <span className="lifecycle-marker" aria-hidden="true" />
              <div className="lifecycle-copy">
                <strong>{step.title}</strong>
                <p>{step.description}</p>
                <time dateTime={step.timestamp || undefined}>{formatDate(step.timestamp)}</time>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="control-card event-times" aria-labelledby="times-title">
        <span className="section-label">Horários</span>
        <h2 id="times-title">Referências do evento</h2>
        <dl className="proof-grid">
          <div><dt>Início programado</dt><dd>{formatDate(event.startsAt)}</dd></div>
          <div><dt>Encerramento programado</dt><dd>{formatDate(event.endsAt)}</dd></div>
          <div><dt>Captura iniciada</dt><dd>{formatDate(event.openedAt)}</dd></div>
          <div><dt>Limite de entrada</dt><dd>{formatDate(event.finalizationCutoffAt)}</dd></div>
          <div><dt>Pool finalizado</dt><dd>{formatDate(event.closedAt)}</dd></div>
        </dl>
      </section>

      {resultNotice && <div className="notice notice-danger" role="status">{resultNotice}</div>}

      {result && (
        <section className="winner-card" aria-labelledby="winner-title">
          <span className="section-label">Resultado oficial</span>
          <div className="winner-avatar" aria-hidden="true">
            {(result.winnerDisplayName || result.winnerUsername || '?').slice(0, 1).toUpperCase()}
          </div>
          <h2 id="winner-title" className="winner-name">
            {result.winnerDisplayName || result.winnerUsername}
          </h2>
          <p className="winner-meta">
            {result.winnerUsername ? `@${result.winnerUsername} · ` : ''}sorteado em {formatDate(result.selectedAt)}
          </p>
          <Link className="btn btn-secondary" to={`/events/${event.id}/result`}>
            Ver registro do sorteio
          </Link>
        </section>
      )}
    </div>
  );
}
