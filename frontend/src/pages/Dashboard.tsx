import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getEventStats, getEvents, getOAuthSession, startOAuth } from '../api/client';
import type { EventResponse, EventStatsResponse, OAuthSessionResponse } from '../api/client';

const numberFormatter = new Intl.NumberFormat('pt-BR');

function formatNumber(value: number | null | undefined) {
  return numberFormatter.format(value ?? 0);
}

function formatLast24h(last24h: EventStatsResponse['last24h'] | undefined) {
  if (typeof last24h === 'number') {
    return `${formatNumber(last24h)} acoes`;
  }

  const votes = formatNumber(last24h?.votes);
  const subs = formatNumber(last24h?.subs);
  const giftedSubs = formatNumber(last24h?.giftedSubs);

  return `${votes} votos / ${subs} subs / ${giftedSubs} gifts`;
}

export default function Dashboard() {
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [stats, setStats] = useState<EventStatsResponse | null>(null);
  const [oauth, setOAuth] = useState<OAuthSessionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [oauthError, setOAuthError] = useState<string | null>(null);
  const [connectError, setConnectError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;

    async function loadDashboard() {
      setLoading(true);
      setError(null);
      setStatsError(null);
      setOAuthError(null);
      setStats(null);

      try {
        const [eventsResult, oauthResult] = await Promise.allSettled([
          getEvents('OPEN'),
          getOAuthSession(),
        ]);

        if (!alive) return;

        if (oauthResult.status === 'fulfilled') {
          setOAuth(oauthResult.value);
        } else {
          setOAuth(null);
          setOAuthError('Nao foi possivel verificar a conexao OAuth.');
        }

        if (eventsResult.status === 'rejected') {
          setEvents([]);
          setError('Nao foi possivel carregar os eventos abertos.');
          return;
        }

        const openEvents = eventsResult.value;
        setEvents(openEvents);

        const featuredEvent = openEvents[0];
        if (!featuredEvent) return;

        try {
          const eventStats = await getEventStats(featuredEvent.id);
          if (alive) setStats(eventStats);
        } catch {
          if (alive) {
            setStatsError('Nao foi possivel carregar as estatisticas do evento em destaque.');
          }
        }
      } finally {
        if (alive) setLoading(false);
      }
    }

    loadDashboard();

    return () => {
      alive = false;
    };
  }, []);

  const handleConnect = async () => {
    setConnectError(null);
    try {
      const { authorizationUrl } = await startOAuth();
      window.location.href = authorizationUrl;
    } catch {
      setConnectError('Nao foi possivel iniciar a conexao OAuth.');
    }
  };

  const connected = oauth?.connected === true;
  const featuredEvent = events[0] ?? null;
  const statCards = stats
    ? [
        { value: formatNumber(stats.totalVotes), label: 'Votos' },
        { value: formatNumber(stats.totalSubs), label: 'Subs' },
        { value: formatNumber(stats.totalGiftedSubs), label: 'Gifted subs' },
        { value: formatNumber(stats.participants), label: 'Participantes' },
        { value: formatNumber(stats.totalEntries), label: 'Entradas' },
        { value: formatLast24h(stats.last24h), label: 'Ultimas 24h' },
      ]
    : [];

  return (
    <div style={{ padding: '32px 40px', maxWidth: 960 }}>
      <h1 className="page-title">Blaze Event Hub</h1>
      <p className="page-subtitle">Eventos comunitarios para a Blaze.stream</p>

      {!connected && (
        <div className="cta-banner">
          <div>
            <h3>Conecte sua conta Blaze</h3>
            <p>{oauthError || 'Necessario para criar eventos e participar de giveaways'}</p>
          </div>
          <button onClick={handleConnect} className="btn btn-primary" style={{ flexShrink: 0 }}>
            Conectar
          </button>
        </div>
      )}

      {connectError && (
        <div className="empty" style={{ color: 'var(--danger)', padding: '0 0 24px' }}>
          {connectError}
        </div>
      )}

      {loading && <div className="empty">Carregando dashboard...</div>}

      {!loading && error && (
        <div className="empty" style={{ color: 'var(--danger)' }}>
          {error}
        </div>
      )}

      {!loading && !error && !featuredEvent && (
        <>
          <div className="section-label">
            Eventos Abertos <span className="count">0</span>
          </div>
          <div className="empty">Nenhum evento aberto no momento</div>
        </>
      )}

      {!loading && !error && featuredEvent && (
        <>
          <div className="section-label">
            Evento em destaque <span className="count">OPEN</span>
          </div>

          <Link
            to={`/events/${featuredEvent.id}`}
            className="card"
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
              padding: '20px 22px',
              marginBottom: 14,
            }}
          >
            <span className="pill pill--open">Aberto</span>
            <div className="card-title" style={{ fontSize: 16 }}>
              {featuredEvent.title}
            </div>
            <div
              className="card-desc"
              style={{
                whiteSpace: 'normal',
                maxWidth: 'none',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
              }}
            >
              {featuredEvent.description || 'Sem descricao'}
            </div>
            <div
              style={{
                marginTop: 8,
                paddingTop: 10,
                borderTop: '1px solid var(--border-card)',
                color: 'var(--accent-light)',
                fontSize: 12,
                fontWeight: 510,
              }}
            >
              Ver evento
            </div>
          </Link>

          {statsError ? (
            <div className="empty" style={{ color: 'var(--danger)', padding: '18px 0 32px' }}>
              {statsError}
            </div>
          ) : (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(136px, 1fr))',
              gap: 12,
              marginBottom: 32,
            }}>
              {statCards.map((item) => (
                <div key={item.label} className="card" style={{ padding: '16px 18px' }}>
                  <div style={{
                    fontSize: item.label === 'Ultimas 24h' ? 14 : 24,
                    fontWeight: 600,
                    color: 'var(--fg)',
                    fontFamily: 'var(--font-mono)',
                    lineHeight: 1.25,
                    overflowWrap: 'anywhere',
                  }}>
                    {item.value}
                  </div>
                  <div style={{
                    fontSize: 11,
                    color: 'var(--muted)',
                    marginTop: 4,
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                  }}>
                    {item.label}
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="section-label">
            Eventos Abertos <span className="count">{events.length}</span>
          </div>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
            gap: 12,
            marginBottom: 32,
          }}>
            {events.map((ev) => (
              <Link
                key={ev.id}
                to={`/events/${ev.id}`}
                className="card"
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                  padding: '18px 20px',
                  minHeight: 140,
                }}
              >
                <span className="pill pill--open">Aberto</span>
                <div className="card-title" style={{
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                }}>
                  {ev.title}
                </div>
                <div className="card-desc" style={{
                  lineHeight: 1.45,
                  whiteSpace: 'normal',
                  maxWidth: 'none',
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                }}>
                  {ev.description?.slice(0, 80) || 'Sem descricao'}
                </div>
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginTop: 'auto',
                  paddingTop: 10,
                  borderTop: '1px solid var(--border-card)',
                  fontSize: 11,
                  color: 'var(--muted)',
                  fontFamily: 'var(--font-mono)',
                  letterSpacing: '0.02em',
                }}>
                  <span>{ev.rules?.length || 0} regras / {ev.participantCount || 0} part.</span>
                  <span style={{ color: 'var(--accent-light)' }}>Ver</span>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}

      {connected && (
        <div style={{ marginTop: 32, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Link to="/events/create" className="btn btn-secondary">+ Criar evento</Link>
          <Link to="/events" className="btn btn-secondary">Ver todos os eventos</Link>
        </div>
      )}
    </div>
  );
}