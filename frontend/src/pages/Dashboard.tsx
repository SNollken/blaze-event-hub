import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getEventStats, getEvents, getOAuthSession, startOAuth } from '../api/client';
import type { EventResponse, EventStatsResponse, OAuthSessionResponse } from '../api/client';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

function formatLast24h(
  last24h: EventStatsResponse['last24h'] | undefined,
  formatNumber: (value: number | null | undefined) => string,
  t: (key: TranslationKey, params?: Record<string, string | number>) => string,
) {
  if (typeof last24h === 'number') {
    return t('actionsCount', { count: formatNumber(last24h) });
  }

  const votes = formatNumber(last24h?.votes);
  const subs = formatNumber(last24h?.subs);
  const giftedSubs = formatNumber(last24h?.giftedSubs);

  return t('dashboardLast24hBreakdown', { votes, subs, giftedSubs });
}

export default function Dashboard() {
  const { lang, t } = useI18n();
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [stats, setStats] = useState<EventStatsResponse | null>(null);
  const [oauth, setOAuth] = useState<OAuthSessionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [statsError, setStatsError] = useState<TranslationKey | null>(null);
  const [oauthError, setOAuthError] = useState<TranslationKey | null>(null);
  const [connectError, setConnectError] = useState<TranslationKey | null>(null);
  const numberFormatter = useMemo(() => new Intl.NumberFormat(lang), [lang]);
  const formatNumber = (value: number | null | undefined) => numberFormatter.format(value ?? 0);

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
          setOAuthError('oauthStatusError');
        }

        if (eventsResult.status === 'rejected') {
          setEvents([]);
          setError('openEventsLoadError');
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
            setStatsError('featuredStatsLoadError');
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
      setConnectError('oauthStartError');
    }
  };

  const connected = oauth?.connected === true;
  const featuredEvent = events[0] ?? null;
  const statCards = stats
    ? [
        { value: formatNumber(stats.totalVotes), label: t('totalVotes') },
        { value: formatNumber(stats.totalSubs), label: t('totalSubs') },
        { value: formatNumber(stats.totalGiftedSubs), label: t('totalGiftedSubs') },
        { value: formatNumber(stats.participants), label: t('participants') },
        { value: formatNumber(stats.totalEntries), label: t('totalEntries') },
        { value: formatLast24h(stats.last24h, formatNumber, t), label: t('last24h'), isLast24h: true },
      ]
    : [];

  return (
    <div style={{ padding: '32px 40px', maxWidth: 960 }}>
      <h1 className="page-title">{t('dashTitle')}</h1>
      <p className="page-subtitle">{t('dashSub')}</p>

      {!connected && (
        <div className="cta-banner">
          <div>
            <h3>{t('connectTitle')}</h3>
            <p>{oauthError ? t(oauthError) : t('connectDesc')}</p>
          </div>
          <button onClick={handleConnect} className="btn btn-primary" style={{ flexShrink: 0 }}>
            {t('connectBtn')}
          </button>
        </div>
      )}

      {connectError && (
        <div className="empty" style={{ color: 'var(--danger)', padding: '0 0 24px' }}>
          {t(connectError)}
        </div>
      )}

      {loading && <div className="empty">{t('dashboardLoading')}</div>}

      {!loading && error && (
        <div className="empty" style={{ color: 'var(--danger)' }}>
          {t(error)}
        </div>
      )}

      {!loading && !error && !featuredEvent && (
        <>
          <div className="section-label">
            {t('sectionOpen')} <span className="count">0</span>
          </div>
          <div className="empty">{t('noOpenEvents')}</div>
        </>
      )}

      {!loading && !error && featuredEvent && (
        <>
          <div className="section-label">
            {t('featuredEvent')} <span className="count">{t('statusOpen')}</span>
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
            <span className="pill pill--open">{t('statusOpen')}</span>
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
              {featuredEvent.description || t('noDescription')}
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
              {t('viewEvent')}
            </div>
          </Link>

          {statsError ? (
            <div className="empty" style={{ color: 'var(--danger)', padding: '18px 0 32px' }}>
              {t(statsError)}
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
                    fontSize: item.isLast24h ? 14 : 24,
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
            {t('sectionOpen')} <span className="count">{events.length}</span>
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
                <span className="pill pill--open">{t('statusOpen')}</span>
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
                  {ev.description?.slice(0, 80) || t('noDescription')}
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
                  <span>{t('rulesParticipantsMeta', { rules: ev.rules?.length || 0, participants: ev.participantCount || 0 })}</span>
                  <span style={{ color: 'var(--accent-light)' }}>{t('view')}</span>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}

      {connected && (
        <div style={{ marginTop: 32, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Link to="/events/create" className="btn btn-secondary">{t('quickCreate')}</Link>
          <Link to="/events" className="btn btn-secondary">{t('viewAllEvents')}</Link>
        </div>
      )}
    </div>
  );
}
