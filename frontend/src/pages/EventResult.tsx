import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  getEvent,
  getEventResult,
  type EventResponse,
  type EventResultResponse,
} from '../api/client';
import { useI18n } from '../i18n/I18nContext';
import type { Lang } from '../i18n/translations';

function localeFor(lang: Lang) {
  return lang === 'pt-BR' ? 'pt-BR' : 'en';
}

export default function EventResult() {
  const { id } = useParams<{ id: string }>();
  const { lang, t } = useI18n();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [result, setResult] = useState<EventResultResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const dateFormatter = useMemo(() => new Intl.DateTimeFormat(localeFor(lang), {
    dateStyle: 'long',
    timeStyle: 'medium',
  }), [lang]);
  const numberFormatter = useMemo(() => new Intl.NumberFormat(localeFor(lang)), [lang]);

  const formatDate = (value: string | null | undefined) => {
    if (!value) return t('eventResultDateMissing');
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? t('eventResultDateUnavailable') : dateFormatter.format(date);
  };

  useEffect(() => {
    let active = true;
    const abortController = new AbortController();

    async function loadResult(eventId: string) {
      setLoading(true);
      setFailed(false);
      setEvent(null);
      setResult(null);

      try {
        const [loadedEvent, loadedResult] = await Promise.all([
          getEvent(eventId, abortController.signal),
          getEventResult(eventId, abortController.signal),
        ]);
        if (!active) return;
        setEvent(loadedEvent);
        setResult(loadedResult);
      } catch (err) {
        if (active && err instanceof DOMException && err.name === 'AbortError') return;
        if (active) {
          setEvent(null);
          setResult(null);
          setFailed(true);
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    if (id) {
      void loadResult(id);
    } else {
      setFailed(true);
      setLoading(false);
    }

    return () => {
      active = false;
      abortController.abort();
    };
  }, [id]);

  useEffect(() => {
    const title = event
      ? t('eventResultDocumentTitle', { title: event.title })
      : t('metaResultTitle');
    const description = event
      ? t('eventResultMetaDescription', { title: event.title })
      : t('metaResultDescription');
    document.title = title;
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', description);
    document.querySelector<HTMLMetaElement>('meta[property="og:title"]')?.setAttribute('content', title);
    document.querySelector<HTMLMetaElement>('meta[property="og:description"]')?.setAttribute('content', description);
  }, [event, t]);

  if (loading) {
    return <div className="hub-page"><div className="empty" role="status">{t('eventResultLoading')}</div></div>;
  }

  if (failed || !event || !result) {
    return (
      <div className="hub-page result-page">
        <div className="empty-state" role="alert">
          <h1 className="empty-state-title">{t('eventResultUnavailableTitle')}</h1>
          <p className="empty-state-desc">{t('eventResultUnavailableDescription')}</p>
          <Link className="btn btn-secondary" to={id ? `/events/${id}` : '/events'}>
            {t('eventResultBackToGiveaway')}
          </Link>
        </div>
      </div>
    );
  }

  const winnerName = result.winnerDisplayName || result.winnerUsername || t('eventResultWinnerFallback');
  const winnerReference = result.winnerUsername ? `@${result.winnerUsername}` : winnerName;

  return (
    <div className="hub-page result-page">
      <header className="page-hero result-hero">
        <div className="page-hero-copy">
          <span className="page-eyebrow">{t('eventResultEyebrow')}</span>
          <p className="result-event-name">{event.title}</p>
          <h1 className="page-title">{winnerName}</h1>
          <p className="page-subtitle">
            {winnerReference} {t('eventResultWonPrize')} <strong>{event.prize}</strong>.
          </p>
        </div>
        <span className="pill pill--completed">{t('eventResultDrawCompleted')}</span>
      </header>

      <section className="winner-card result-winner" aria-labelledby="result-winner-title">
        <div className="winner-avatar" aria-hidden="true">{winnerName.slice(0, 1).toUpperCase()}</div>
        <h2 id="result-winner-title" className="winner-name">{t('eventResultConfirmedWinner')}</h2>
        {result.winnerUsername && (
          <p className="winner-meta">{t('eventResultBlazeProfile')} @{result.winnerUsername}</p>
        )}
        <time className="result-timestamp" dateTime={result.selectedAt}>
          {t('eventResultDrawnOn')} {formatDate(result.selectedAt)}
        </time>
      </section>

      <section className="control-card result-proof" aria-labelledby="proof-title">
        <div className="section-heading">
          <div>
            <span className="section-label">{t('eventResultPersistedData')}</span>
            <h2 id="proof-title">{t('eventResultTechnicalRecord')}</h2>
          </div>
        </div>
        <p className="proof-intro">{t('eventResultProofIntro')}</p>
        <dl className="proof-grid">
          <div>
            <dt>{t('eventResultMethod')}</dt>
            <dd><code>{result.drawMethod}</code></dd>
          </div>
          <div>
            <dt>{t('eventResultPoolParticipants')}</dt>
            <dd>{numberFormatter.format(result.participantCount)}</dd>
          </div>
          <div className="proof-wide">
            <dt>{t('eventResultRecordedSeed')}</dt>
            <dd><code>{result.drawSeed}</code></dd>
          </div>
          <div className="proof-wide">
            <dt>{t('eventResultPoolHash')}</dt>
            <dd><code>{result.poolHash}</code></dd>
          </div>
          <div>
            <dt>{t('eventResultPoolFinalized')}</dt>
            <dd>{formatDate(event.closedAt)}</dd>
          </div>
          <div>
            <dt>{t('eventResultPersisted')}</dt>
            <dd>{formatDate(result.selectedAt)}</dd>
          </div>
        </dl>
      </section>

      <div className="page-actions">
        <Link className="btn btn-secondary" to={`/events/${event.id}`}>{t('eventResultViewDetails')}</Link>
        <Link className="btn btn-primary" to="/events">{t('eventResultExploreOthers')}</Link>
      </div>
    </div>
  );
}
