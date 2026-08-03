import { useCallback } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { StatusDot } from '../components/Badge';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { getStatus, getEventsStatus, getOAuthSession } from '../api/client';
import { t } from '../i18n';
import {
  Server,
  Key,
  Radio,
  Layers,
  Clock,
  Zap,
  Settings,
} from 'lucide-react';

function formatUptime(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}min`;
  return `${m}min`;
}

export default function Dashboard() {
  const fetchStatus = useCallback(() => getStatus(), []);
  const fetchEvents = useCallback(() => getEventsStatus(), []);
  const fetchOAuth = useCallback(() => getOAuthSession(), []);

  const { data: status, loading: statusLoading, error: statusError } = usePolling(fetchStatus, 10000);
  const { data: events, loading: eventsLoading, error: eventsError } = usePolling(fetchEvents, 8000);
  const { data: oauth, loading: oauthLoading, error: oauthError } = usePolling(fetchOAuth, 15000);

  const pollError = statusError || eventsError || oauthError;

  if (statusLoading && !status) {
    return (
      <Layout title={t('dashboard.title')}>
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div>{t('common.loading')}</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout title={t('dashboard.title')} subtitle={t('dashboard.subtitle')}>
      {pollError && <ErrorBanner error={pollError} onRetry={() => window.location.reload()} />}
      {/* Stats row */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title={t('dashboard.backend')}
          value={status ? t('common.online') : t('common.offline')}
          icon={<Server size={18} />}
          color={status ? 'success' : 'error'}
          subtitle={status ? `${status.appName} ${status.version}` : t('dashboard.unavailable')}
        />
        <StatsCard
          title={t('dashboard.blazeOAuth')}
          value={status?.oauthConnected ? t('common.connected') : t('common.disconnected')}
          icon={<Key size={18} />}
          color={status?.oauthConnected ? 'success' : 'warning'}
          subtitle={status?.connectedAccountDisplayName || t('dashboard.noAccount')}
        />
        <StatsCard
          title={t('dashboard.eventsSocket')}
          value={events?.runnerRunning ? t('common.running') : t('common.stopped')}
          icon={<Radio size={18} />}
          color={events?.runnerRunning ? 'success' : 'neutral'}
          subtitle={events?.clientRunning ? t('dashboard.clientConnected') : t('dashboard.clientDisconnected')}
        />
        <StatsCard
          title={t('dashboard.overlays')}
          value={status?.overlaysCount ?? 0}
          icon={<Layers size={18} />}
          color="accent"
          subtitle={`${status?.activeProfilesCount ?? 0} ${t('dashboard.profiles')}`}
        />
        <StatsCard
          title={t('dashboard.java')}
          value={status?.javaVersion ?? '-'}
          icon={<Zap size={18} />}
          color="primary"
        />
        <StatsCard
          title={t('dashboard.uptime')}
          value={status ? formatUptime(status.uptimeSeconds) : '-'}
          icon={<Clock size={18} />}
          color="primary"
        />
      </div>

      {/* Status details */}
      <div className="responsive-grid-2" style={{ marginBottom: 24 }}>
        {/* System status */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>{t('dashboard.systemStatus')}</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <StatusItem label={t('dashboard.oauthConfigured')} ok={status?.blazeOAuthConfigured} />
            <StatusItem label={t('dashboard.blazeApiConfigured')} ok={status?.blazeApiConfigured} />
            <StatusItem label={t('dashboard.socketConfigured')} ok={status?.socketConfigured} />
            <StatusItem label={t('dashboard.tokenPresent')} ok={status?.tokenPresent} />
            <StatusItem label={t('dashboard.refreshCredential')} ok={status?.refreshCredentialPresent} />
            <StatusItem label={t('dashboard.monitoredChannel')} ok={status?.monitoredChannelConfigured} />
            <StatusItem label={t('dashboard.sessionId')} ok={status?.sessionIdPresent} />
          </div>
        </div>

        {/* Account & OAuth */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>{t('dashboard.accountOAuth')}</h3>
          {oauth?.connected ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {oauth.profile?.avatarUrl && (
                  <img
                    src={oauth.profile.avatarUrl}
                    alt={oauth.profile.displayName ? `${oauth.profile.displayName} ${t('common.avatar')}` : ''}
                    style={{ width: 36, height: 36, borderRadius: '50%' }}
                  />
                )}
                <div>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>
                    {oauth.profile?.displayName || oauth.userId}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    @{oauth.profile?.username || t('common.unknown')}
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12, color: 'var(--text-secondary)' }}>
                <div>{t('blaze.scopes')} {oauth.scopes?.join(', ') || t('common.none')}</div>
                <div>{t('dashboard.tokenPresent')}: {oauth.tokenPresent ? t('common.present') : t('common.absent')}</div>
                <div>{t('dashboard.refreshCredential')}: {oauth.refreshCredentialPresent ? t('common.present') : t('common.absent')}</div>
                {oauth.tokenExpiredOrUnknown && (
                  <div style={{ color: 'var(--warning)' }}>{t('common.expired')}</div>
                )}
              </div>
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              {t('dashboard.noAccount')}
              {status?.nextRecommendedAction && (
                <div style={{ marginTop: 8, color: 'var(--accent)' }}>
                  {t('dashboard.nextAction')} {status.nextRecommendedAction}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Recommended action */}
      {status?.nextRecommendedAction && (
        <div
          className="glass-card"
          style={{
            padding: '14px 20px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            borderColor: 'var(--accent)',
          }}
        >
          <Settings size={16} style={{ color: 'var(--accent)', flexShrink: 0 }} />
          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            {status.nextRecommendedAction}
          </span>
        </div>
      )}
    </Layout>
  );
}

function StatusItem({ label, ok }: { label: string; ok?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{label}</span>
      <StatusDot status={ok ? 'active' : 'inactive'} label={ok ? t('common.yes') : t('common.no')} />
    </div>
  );
}
