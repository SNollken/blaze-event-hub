import { useCallback } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { StatusDot } from '../components/Badge';
import { usePolling } from '../components/Toast';
import { getStatus, getEventsStatus, getOAuthSession } from '../api/client';
import {
  Server,
  Key,
  Radio,
  Layers,
  Clock,
  User,
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

  const { data: status, loading: statusLoading } = usePolling(fetchStatus, 10000);
  const { data: events } = usePolling(fetchEvents, 8000);
  const { data: oauth } = usePolling(fetchOAuth, 15000);

  if (statusLoading && !status) {
    return (
      <Layout title="Visao Geral">
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div>Carregando...</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout title="Visao Geral" subtitle="Painel do NollenBlaze">
      {/* Stats row */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Backend"
          value={status ? 'Online' : 'Offline'}
          icon={<Server size={18} />}
          color={status ? 'success' : 'error'}
          subtitle={status ? `${status.appName} ${status.version}` : 'Indisponivel'}
        />
        <StatsCard
          title="Blaze OAuth"
          value={status?.oauthConnected ? 'Conectado' : 'Desconectado'}
          icon={<Key size={18} />}
          color={status?.oauthConnected ? 'success' : 'warning'}
          subtitle={status?.connectedAccountDisplayName || 'Sem conta conectada'}
        />
        <StatsCard
          title="Events Socket"
          value={events?.runnerRunning ? 'Rodando' : 'Parado'}
          icon={<Radio size={18} />}
          color={events?.runnerRunning ? 'success' : 'neutral'}
          subtitle={events?.clientRunning ? 'Cliente conectado' : 'Cliente desconectado'}
        />
        <StatsCard
          title="Overlays"
          value={status?.overlaysCount ?? 0}
          icon={<Layers size={18} />}
          color="accent"
          subtitle={`${status?.activeProfilesCount ?? 0} perfis`}
        />
        <StatsCard
          title="Java"
          value={status?.javaVersion ?? '-'}
          icon={<Zap size={18} />}
          color="primary"
        />
        <StatsCard
          title="Uptime"
          value={status ? formatUptime(status.uptimeSeconds) : '-'}
          icon={<Clock size={18} />}
          color="primary"
        />
      </div>

      {/* Status details */}
      <div className="responsive-grid-2" style={{ marginBottom: 24 }}>
        {/* System status */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Status do Sistema</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <StatusItem label="OAuth configurado" ok={status?.blazeOAuthConfigured} />
            <StatusItem label="Blaze API configurada" ok={status?.blazeApiConfigured} />
            <StatusItem label="Socket configurado" ok={status?.socketConfigured} />
            <StatusItem label="Token presente" ok={status?.tokenPresent} />
            <StatusItem label="Refresh credential" ok={status?.refreshCredentialPresent} />
            <StatusItem label="Canal monitorado" ok={status?.monitoredChannelConfigured} />
            <StatusItem label="Session ID" ok={status?.sessionIdPresent} />
          </div>
        </div>

        {/* Account & OAuth */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Conta e OAuth</h3>
          {oauth?.connected ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {oauth.profile?.avatarUrl && (
                  <img
                    src={oauth.profile.avatarUrl}
                    alt=""
                    style={{ width: 36, height: 36, borderRadius: '50%' }}
                  />
                )}
                <div>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>
                    {oauth.profile?.displayName || oauth.userId}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    @{oauth.profile?.username || 'desconhecido'}
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12, color: 'var(--text-secondary)' }}>
                <div>Scopes: {oauth.scopes?.join(', ') || 'nenhum'}</div>
                <div>Token: {oauth.tokenPresent ? 'Presente' : 'Ausente'}</div>
                <div>Refresh: {oauth.refreshCredentialPresent ? 'Presente' : 'Ausente'}</div>
                {oauth.tokenExpiredOrUnknown && (
                  <div style={{ color: 'var(--warning)' }}>Token expirado ou status desconhecido</div>
                )}
              </div>
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              Nenhuma conta Blaze conectada.
              {status?.nextRecommendedAction && (
                <div style={{ marginTop: 8, color: 'var(--accent)' }}>
                  Proxima acao: {status.nextRecommendedAction}
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
      <StatusDot status={ok ? 'active' : 'inactive'} label={ok ? 'Sim' : 'Nao'} />
    </div>
  );
}
