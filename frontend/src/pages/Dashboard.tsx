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
  if (h > 0) return `${h}${t('common.hour')} ${m}${t('common.minute')}`;
  return `${m}${t('common.minute')}`;
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
        <div className="empty-state min-h-[300px]">
          <div>{t('common.loading')}</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout title={t('dashboard.title')} subtitle={t('dashboard.subtitle')}>
      {pollError && <ErrorBanner error={pollError} onRetry={() => window.location.reload()} />}
      {/* Stats row */}
      <div className="stats-grid mb-6">
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
      <div className="responsive-grid-2 mb-6">
        {/* System status */}
        <div className="glass-card p-5">
          <h3 className="text-sm font-semibold mb-4">{t('dashboard.systemStatus')}</h3>
          <div className="flex flex-col gap-3">
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
        <div className="glass-card p-5">
          <h3 className="text-sm font-semibold mb-4">{t('dashboard.accountOAuth')}</h3>
          {oauth?.connected ? (
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2.5">
                {oauth.profile?.avatarUrl && (
                  <img
                    src={oauth.profile.avatarUrl}
                    alt={oauth.profile.displayName ? `${oauth.profile.displayName} ${t('common.avatar')}` : ''}
                    className="w-9 h-9 rounded-full"
                  />
                )}
                <div>
                  <div className="font-semibold text-sm">
                    {oauth.profile?.displayName || oauth.userId}
                  </div>
                  <div className="text-xs text-text-muted">
                    @{oauth.profile?.username || t('common.unknown')}
                  </div>
                </div>
              </div>
              <div className="flex flex-col gap-1.5 text-xs text-text-secondary">
                <div>{t('blaze.scopes')} {oauth.scopes?.join(', ') || t('common.none')}</div>
                <div>{t('dashboard.tokenPresent')}: {oauth.tokenPresent ? t('common.present') : t('common.absent')}</div>
                <div>{t('dashboard.refreshCredential')}: {oauth.refreshCredentialPresent ? t('common.present') : t('common.absent')}</div>
                {oauth.tokenExpiredOrUnknown && (
                  <div className="text-warning">{t('common.expired')}</div>
                )}
              </div>
            </div>
          ) : (
            <div className="text-[13px] text-text-muted">
              {t('dashboard.noAccount')}
              {status?.nextRecommendedAction && (
                <div className="mt-2 text-accent">
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
          className="glass-card p-3.5 px-5 flex items-center gap-2.5 border-accent"
        >
          <Settings size={16} className="text-accent shrink-0" />
          <span className="text-[13px] text-text-secondary">
            {status.nextRecommendedAction}
          </span>
        </div>
      )}
    </Layout>
  );
}

function StatusItem({ label, ok }: { label: string; ok?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-[13px] text-text-secondary">{label}</span>
      <StatusDot status={ok ? 'active' : 'inactive'} label={ok ? t('common.yes') : t('common.no')} />
    </div>
  );
}
