import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  getEvent,
  getEventResult,
  type EventResponse,
  type EventResultResponse,
} from '../api/client';

const dateFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'long',
  timeStyle: 'medium',
});

const numberFormatter = new Intl.NumberFormat('pt-BR');

function formatDate(value: string | null | undefined) {
  if (!value) return 'Não informado';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Data indisponível' : dateFormatter.format(date);
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

export default function EventResult() {
  const { id } = useParams<{ id: string }>();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [result, setResult] = useState<EventResultResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    async function loadResult(eventId: string) {
      setLoading(true);
      setError('');

      try {
        const [loadedEvent, loadedResult] = await Promise.all([
          getEvent(eventId),
          getEventResult(eventId),
        ]);
        if (!active) return;
        setEvent(loadedEvent);
        setResult(loadedResult);
        document.title = `${loadedEvent.title}: resultado | Blaze Event Hub`;
      } catch (loadError) {
        if (active) {
          setEvent(null);
          setResult(null);
          setError(getErrorMessage(
            loadError,
            'O resultado ainda não foi publicado ou este giveaway não existe.',
          ));
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    if (id) {
      void loadResult(id);
    } else {
      setError('O identificador do giveaway não foi informado.');
      setLoading(false);
    }

    return () => {
      active = false;
    };
  }, [id]);

  if (loading) {
    return <div className="hub-page"><div className="empty" role="status">Carregando resultado…</div></div>;
  }

  if (error || !event || !result) {
    return (
      <div className="hub-page result-page">
        <div className="empty-state" role="alert">
          <h1 className="empty-state-title">Resultado indisponível</h1>
          <p className="empty-state-desc">{error || 'Este resultado ainda não foi publicado.'}</p>
          <Link className="btn btn-secondary" to={id ? `/events/${id}` : '/events'}>
            Voltar ao giveaway
          </Link>
        </div>
      </div>
    );
  }

  const winnerName = result.winnerDisplayName || result.winnerUsername || 'Vencedor Blaze';

  return (
    <div className="hub-page result-page">
      <header className="page-hero result-hero">
        <div className="page-hero-copy">
          <span className="page-eyebrow">Resultado público registrado</span>
          <p className="result-event-name">{event.title}</p>
          <h1 className="page-title">{winnerName}</h1>
          <p className="page-subtitle">
            {result.winnerUsername ? `@${result.winnerUsername}` : winnerName} venceu o prêmio <strong>{event.prize}</strong>.
          </p>
        </div>
        <span className="pill pill--completed">Sorteio concluído</span>
      </header>

      <section className="winner-card result-winner" aria-labelledby="result-winner-title">
        <div className="winner-avatar" aria-hidden="true">{winnerName.slice(0, 1).toUpperCase()}</div>
        <h2 id="result-winner-title" className="winner-name">Vencedor confirmado</h2>
        {result.winnerUsername && (
          <p className="winner-meta">Perfil Blaze: @{result.winnerUsername}</p>
        )}
        <time className="result-timestamp" dateTime={result.selectedAt}>
          Sorteado em {formatDate(result.selectedAt)}
        </time>
      </section>

      <section className="control-card result-proof" aria-labelledby="proof-title">
        <div className="section-heading">
          <div>
            <span className="section-label">Dados persistidos</span>
            <h2 id="proof-title">Registro técnico do sorteio</h2>
          </div>
        </div>
        <p className="proof-intro">
          O servidor registrou o conjunto de participantes antes de escolher uma pessoa. Os dados abaixo
          são os identificadores técnicos persistidos com o resultado; sozinhos, eles não reconstituem a lista do pool.
        </p>
        <dl className="proof-grid">
          <div>
            <dt>Método</dt>
            <dd><code>{result.drawMethod}</code></dd>
          </div>
          <div>
            <dt>Participantes no pool</dt>
            <dd>{numberFormatter.format(result.participantCount)}</dd>
          </div>
          <div className="proof-wide">
            <dt>Seed registrada</dt>
            <dd><code>{result.drawSeed}</code></dd>
          </div>
          <div className="proof-wide">
            <dt>Hash SHA-256 do pool</dt>
            <dd><code>{result.poolHash}</code></dd>
          </div>
          <div>
            <dt>Pool finalizado</dt>
            <dd>{formatDate(event.closedAt)}</dd>
          </div>
          <div>
            <dt>Resultado persistido</dt>
            <dd>{formatDate(result.selectedAt)}</dd>
          </div>
        </dl>
      </section>

      <div className="page-actions">
        <Link className="btn btn-secondary" to={`/events/${event.id}`}>Ver detalhes do giveaway</Link>
        <Link className="btn btn-primary" to="/events">Explorar outros giveaways</Link>
      </div>
    </div>
  );
}
