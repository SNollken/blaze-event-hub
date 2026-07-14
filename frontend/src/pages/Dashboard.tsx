import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Radio, Trophy, Users } from 'lucide-react';
import { getEventStats, getEvents, getOAuthSession } from '../api/client';
import type { EventLifecycleStats, EventResponse, OAuthSessionResponse } from '../api/client';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';

function commandLabel(command: string) {
  return command.trim() || '!participar';
}

function captureHealthLabel(stats: EventLifecycleStats | null) {
  if (!stats) return 'Aguardando dados';
  if (stats.captureHealth === 'HEALTHY') return 'Saudável';
  if (stats.captureHealth === 'STARTING') return 'Iniciando';
  if (stats.captureHealth === 'DEGRADED') return 'Com atenção';
  if (stats.captureHealth === 'FINALIZING') return 'Finalizando';
  return 'Inativa';
}

function EventCard({ event }: { event: EventResponse }) {
  return (
    <Link to={`/events/${event.id}`} className="event-card event-card--live">
      <div className="event-card__topline">
        <span className="status-pill status-pill--open">Captando agora</span>
        <span className="event-card__signal" aria-label="Evento aberto para entradas">
          <span aria-hidden="true" /> aberto
        </span>
      </div>
      <div className="event-card__body">
        {event.creatorChannelSlug && (
          <span className="event-card__creator">
            {event.creatorChannelDisplayName || event.creatorChannelSlug} · @{event.creatorChannelSlug}
          </span>
        )}
        <p className="event-card__prize">{event.prize || 'Prêmio a confirmar'}</p>
        <h3 className="event-card__title">{event.title}</h3>
        <p className="event-card__description">
          {event.description || 'Entre no chat da transmissão e use o comando indicado pelo criador.'}
        </p>
      </div>
      <div className="event-card__command">
        <span>Comando de entrada</span>
        <code>{commandLabel(event.entryCommand)}</code>
      </div>
      <span className="event-card__link">
        Ver giveaway <ArrowRight size={16} aria-hidden="true" />
      </span>
    </Link>
  );
}

export default function Dashboard() {
  const { t } = useI18n();
  const fetchEvents = useCallback(() => getEvents('OPEN'), []);
  const fetchOAuth = useCallback(() => getOAuthSession(), []);
  const eventsState = usePolling(fetchEvents, 10_000);
  const oauthState = usePolling(fetchOAuth, 60_000);
  const events = eventsState.data || [];
  const featuredEvent = events[0] ?? null;
  const featuredEventId = featuredEvent?.id || null;
  const fetchFeaturedStats = useCallback(
    () => featuredEventId ? getEventStats(featuredEventId) : Promise.resolve<EventLifecycleStats | null>(null),
    [featuredEventId],
  );
  const featuredStatsState = usePolling(fetchFeaturedStats, 10_000);
  const featuredStats = featuredStatsState.data?.eventId === featuredEventId
    ? featuredStatsState.data
    : null;
  const moreEvents = useMemo(() => events.slice(1, 7), [events]);
  const oauth: OAuthSessionResponse | null = oauthState.data;
  const connected = oauth?.connected === true;
  const loading = eventsState.loading && !eventsState.data;
  const eventsError = Boolean(eventsState.error && !eventsState.data);
  const statsError = Boolean(featuredStatsState.error);

  return (
    <div className="page hub-page hub-page--dashboard">
      <header className="page-hero page-hero--split">
        <div className="page-hero__copy">
          <p className="page-eyebrow">Giveaways da Blaze.stream, em um só lugar</p>
          <h1 className="page-title">{t('dashTitle')}</h1>
          <p className="page-subtitle">
            Descubra eventos ao vivo, entre pelo comando no chat e acompanhe resultados registrados pelo Hub.
          </p>
          <div className="page-hero__actions">
            <Link to="/events" className="btn btn-primary">
              Explorar giveaways <ArrowRight size={17} aria-hidden="true" />
            </Link>
            <Link to={connected ? '/events/create' : '/login'} className="btn btn-secondary">
              {connected ? 'Criar giveaway' : 'Conectar como criador'}
            </Link>
          </div>
        </div>

        <div className="signal-panel" aria-label="Como funciona">
          <span className="signal-panel__label"><Radio size={16} aria-hidden="true" /> Fluxo do evento</span>
          <ol className="lifecycle-mini">
            <li className="is-current"><span>01</span> Criador abre</li>
            <li><span>02</span> Chat entra</li>
            <li><span>03</span> Lista congela</li>
            <li><span>04</span> Sorteio acontece</li>
          </ol>
        </div>
      </header>

      {eventsState.error && eventsState.data && (
        <div className="notice notice--warning" role="status">
          A atualização automática falhou. Os últimos eventos recebidos continuam visíveis.
        </div>
      )}

      <section className="hub-section" aria-labelledby="open-events-heading">
        <div className="section-heading">
          <div>
            <p className="section-heading__eyebrow">Sinal aberto</p>
            <h2 id="open-events-heading">{t('sectionOpen')}</h2>
          </div>
          {!loading && !eventsError && (
            <span className="section-heading__count">{events.length} {events.length === 1 ? 'evento' : 'eventos'}</span>
          )}
        </div>

        {loading && (
          <div className="empty-state" role="status" aria-live="polite">
            <span className="empty-state__signal" aria-hidden="true" />
            <h3>Buscando giveaways ao vivo</h3>
            <p>Aguarde enquanto sincronizamos os eventos abertos.</p>
          </div>
        )}

        {!loading && eventsError && (
          <div className="empty-state empty-state--error" role="alert">
            <h3>Não foi possível carregar os eventos</h3>
            <p>Confira sua conexão e tente novamente.</p>
            <button type="button" className="btn btn-secondary" onClick={() => void eventsState.reload()}>
              Tentar novamente
            </button>
          </div>
        )}

        {!loading && !eventsError && !featuredEvent && (
          <div className="empty-state">
            <Trophy size={30} aria-hidden="true" />
            <h3>Nenhum giveaway captando agora</h3>
            <p>Os próximos eventos abertos aparecerão aqui automaticamente.</p>
            <Link to="/events" className="btn btn-secondary">Ver eventos encerrados</Link>
          </div>
        )}

        {!loading && !eventsError && featuredEvent && (
          <>
            <article className="featured-event">
              <div className="featured-event__content">
                <div className="featured-event__topline">
                  <span className="status-pill status-pill--open">Captando agora</span>
                  <span className="featured-event__live"><span aria-hidden="true" /> evento aberto</span>
                </div>
                <p className="featured-event__prize">{featuredEvent.prize || 'Prêmio a confirmar'}</p>
                <h3>{featuredEvent.title}</h3>
                <p>{featuredEvent.description || 'Use o comando no chat para entrar neste giveaway.'}</p>
                <div className="featured-event__command">
                  <span>Digite no chat</span>
                  <code>{commandLabel(featuredEvent.entryCommand)}</code>
                </div>
                <Link to={`/events/${featuredEvent.id}`} className="btn btn-primary">
                  Abrir evento <ArrowRight size={17} aria-hidden="true" />
                </Link>
              </div>

              <div className="featured-event__telemetry" aria-label="Estado do giveaway em destaque">
                <div className="telemetry-item">
                  <Users size={18} aria-hidden="true" />
                  <strong>{featuredStats?.participantCount ?? '—'}</strong>
                  <span>participantes captados</span>
                </div>
                <div className="telemetry-item">
                  <Radio size={18} aria-hidden="true" />
                  <strong>{captureHealthLabel(featuredStats)}</strong>
                  <span>saúde da captura</span>
                </div>
                {statsError && <p className="telemetry-note" role="status">Métricas temporariamente indisponíveis.</p>}
                <ol className="lifecycle-mini lifecycle-mini--vertical">
                  <li className="is-complete"><span>01</span> Configurado</li>
                  <li className="is-current"><span>02</span> Captando</li>
                  <li><span>03</span> Finalizar</li>
                  <li><span>04</span> Sortear</li>
                </ol>
              </div>
            </article>

            {moreEvents.length > 0 && (
              <div className="event-grid event-grid--compact">
                {moreEvents.map((event) => <EventCard key={event.id} event={event} />)}
              </div>
            )}
          </>
        )}
      </section>

      <section className="creator-cta" aria-labelledby="creator-cta-heading">
        <div>
          <p className="page-eyebrow">Para criadores</p>
          <h2 id="creator-cta-heading">Você encerra a entrada. O Hub congela a lista.</h2>
          <p>O sorteio só é liberado depois da finalização, com o pool de participantes registrado.</p>
        </div>
        <Link to={connected ? '/events/create' : '/login'} className="btn btn-secondary">
          {connected ? 'Configurar novo evento' : 'Conectar conta Blaze'}
        </Link>
      </section>
    </div>
  );
}
