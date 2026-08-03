import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge, StatusDot } from '../components/Badge';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { addToast } from '../components/Toast';
import { t } from '../i18n';
import {
  getStatus,
  getSetupStatus,
  getOAuthSession,
  startOAuth,
  disconnectOAuth,
  refreshOAuth,
} from '../api/client';
import {
  Key,
  Radio,
  RefreshCw,
  ExternalLink,
  Copy,
  Shield,
  CheckCircle,
  XCircle,
} from 'lucide-react';

export default function BlazeChannel() {
  const fetchStatus = useCallback(() => getStatus(), []);
  const fetchSetup = useCallback(() => getSetupStatus(), []);
  const fetchOAuth = useCallback(() => getOAuthSession(), []);

  const { data: status, loading: statusLoading, error: statusError, reload: reloadStatus } = usePolling(fetchStatus, 12000);
  const { data: setup, loading: setupLoading, error: setupError, reload: reloadSetup } = usePolling(fetchSetup, 15000);
  const { data: oauth, loading: oauthLoading, error: oauthError, reload: reloadOAuth } = usePolling(fetchOAuth, 12000);

  const firstError = statusError || setupError || oauthError;
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const handleStartOAuth = async () => {
    setActionLoading('oauth-start');
    try {
      const res = await startOAuth();
      window.open(res.authorizationUrl, '_blank', 'noopener,noreferrer');
      addToast('success', t('blaze.connectSuccess'));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('blaze.connectError'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleRefresh = async () => {
    setActionLoading('refresh');
    try {
      await refreshOAuth();
      addToast('success', t('blaze.refreshSuccess'));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('blaze.refreshError'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleDisconnect = async () => {
    setActionLoading('disconnect');
    try {
      await disconnectOAuth();
      addToast('success', t('blaze.disconnectSuccess'));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('blaze.disconnectError'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleSync = async () => {
    setActionLoading('sync');
    try {
      await reloadSetup();
      await reloadOAuth();
      await reloadStatus();
      addToast('success', t('blaze.syncSuccess'));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('blaze.syncError'));
    } finally {
      setActionLoading(null);
    }
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      addToast('success', t('blaze.copySuccess'));
    } catch {
      addToast('error', t('blaze.copyError'));
    }
  };

  const isOAuthReady = setup?.oauthStartReady;
  const isConnected = oauth?.connected;
  const isTokenPresent = oauth?.tokenPresent;

  return (
    <Layout
      title={t('nav.blaze')}
      subtitle={t('blaze.subtitle')}
      headerActions={
        <button className="btn btn-secondary btn-sm" onClick={handleSync} disabled={actionLoading === 'sync'}>
          <RefreshCw size={14} className={actionLoading === 'sync' ? 'spin' : ''} />
          {t('blaze.sync')}
        </button>
      }
    >
      {/* Error banner */}
      {firstError && <ErrorBanner error={firstError} onRetry={handleSync} />}
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title={t('blaze.oauthConnection')}
          value={oauthLoading ? t('common.loading') : isConnected ? t('common.connected') : t('common.disconnected')}
          icon={<Key size={18} />}
          color={isConnected ? 'success' : 'warning'}
          subtitle={oauthLoading ? '' : oauth?.profile?.displayName || t('blaze.noAccount')}
        />
        <StatsCard
          title={t('blaze.token')}
          value={oauthLoading ? t('common.loading') : isTokenPresent ? t('common.present') : t('common.absent')}
          icon={<Shield size={18} />}
          color={isTokenPresent ? 'success' : 'error'}
          subtitle={oauthLoading ? '' : oauth?.tokenExpiredOrUnknown ? t('common.expired') : t('common.valid')}
        />
        <StatsCard
          title={t('blaze.eventsConfig')}
          value={statusLoading ? t('common.loading') : status?.socketConfigured ? t('common.ready') : t('common.notConfigured')}
          icon={<Radio size={18} />}
          color={statusLoading ? 'neutral' : status?.socketConfigured ? 'success' : 'neutral'}
          subtitle={statusLoading ? '' : setup?.monitoredChannel || ''}
        />
        <StatsCard
          title={t('blaze.monitoredChannel')}
          value={setupLoading ? t('common.loading') : status?.monitoredChannelConfigured ? t('common.configured') : t('common.notConfigured')}
          icon={<Radio size={18} />}
          color={setupLoading ? 'neutral' : status?.monitoredChannelConfigured ? 'success' : 'neutral'}
          subtitle={setupLoading ? '' : setup?.monitoredChannel || ''}
        />
      </div>

      {/* Setup checklist */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 24 }}>
        <div className="section-header" style={{ marginBottom: 16 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600 }}>{t('blaze.checklist')}</h3>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {setup?.checklist?.map((item, i) => (
            <div
              key={i}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 14px',
                background: 'var(--bg-base)',
                borderRadius: 'var(--radius)',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {item.configured ? (
                  <CheckCircle size={16} style={{ color: 'var(--success)' }} />
                ) : (
                  <XCircle size={16} style={{ color: 'var(--text-muted)' }} />
                )}
                <div>
                  <div style={{ fontSize: 13, fontWeight: 500 }}>{item.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{item.help}</div>
                </div>
              </div>
              <Badge variant={item.configured ? 'success' : 'warning'}>{item.status}</Badge>
            </div>
          ))}
        </div>
      </div>

      {/* OAuth & Account */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
        {/* OAuth controls */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>{t('blaze.oauthAuth')}</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {!isConnected ? (
              <button
                className="btn btn-accent"
                onClick={handleStartOAuth}
                disabled={!isOAuthReady || actionLoading === 'oauth-start'}
              >
                <Key size={14} />
                {actionLoading === 'oauth-start' ? t('blaze.connecting') : t('blaze.connect')}
              </button>
            ) : (
              <>
                <button className="btn btn-secondary" onClick={handleRefresh} disabled={actionLoading === 'refresh'}>
                  <RefreshCw size={14} />
                  {actionLoading === 'refresh' ? t('blaze.refreshing') : t('blaze.refresh')}
                </button>
                <button className="btn btn-danger" onClick={handleDisconnect} disabled={actionLoading === 'disconnect'}>
                  <XCircle size={14} />
                  {actionLoading === 'disconnect' ? t('blaze.disconnecting') : t('blaze.disconnect')}
                </button>
              </>
            )}

            {setup?.nextSteps && setup.nextSteps.length > 0 && (
              <div style={{ marginTop: 8 }}>
                <div style={{ fontSize: 12, fontWeight: 500, color: 'var(--text-muted)', marginBottom: 6 }}>
                  {t('blaze.nextSteps')}
                </div>
                {setup.nextSteps.map((step, i) => (
                  <div key={i} style={{ fontSize: 12, color: 'var(--text-secondary)', padding: '3px 0' }}>
                    {i + 1}. {step}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Account info */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>{t('blaze.connectedAccount')}</h3>
          {oauth?.profile ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {oauth.profile.avatarUrl && (
                  <img src={oauth.profile.avatarUrl} alt={oauth.profile.displayName ? `${oauth.profile.displayName} ${t('common.avatar')}` : ''} style={{ width: 40, height: 40, borderRadius: '50%' }} />
                )}
                <div>
                  <div style={{ fontWeight: 600 }}>{oauth.profile.displayName}</div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>@{oauth.profile.username}</div>
                </div>
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                <div>{t('blaze.scopes')} {oauth.scopes?.join(', ') || 'nenhum'}</div>
                {oauth.lastProfileSyncAt && (
                  <div>{t('blaze.lastSync')} {new Date(oauth.lastProfileSyncAt).toLocaleString('pt-BR')}</div>
                )}
              </div>
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              {t('blaze.noAccount')}
            </div>
          )}
        </div>
      </div>

      {/* Redirect URI & Docs */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 24 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>{t('blaze.redirectUri')}</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <label>{t('blaze.redirectUriLabel')}</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <code
                className="mono"
                style={{
                  flex: 1,
                  padding: '6px 10px',
                  background: 'var(--bg-base)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: 12,
                  color: 'var(--text-secondary)',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {setup?.redirectUri || t('common.notConfigured')}
              </code>
              {setup?.redirectUri && (
                <button
                  className="copy-btn"
                  aria-label={t('blaze.copyUri')}
                  onClick={() => copyToClipboard(setup.redirectUri!)}
                >
                  <Copy size={12} />
                  {t('common.copy')}
                </button>
              )}
            </div>
          </div>

          {setup?.docsLinks && setup.docsLinks.length > 0 && (
            <div>
              <label>{t('blaze.links')}</label>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 4 }}>
                {setup.docsLinks.map((link, i) => (
                  <a
                    key={i}
                    href={link.url}
                    target="_blank"
                    rel="noreferrer"
                    className="btn btn-secondary btn-sm"
                    style={{ textDecoration: 'none' }}
                  >
                    <ExternalLink size={12} />
                    {link.title}
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Scopes */}
      {setup?.recommendedScopes && setup.recommendedScopes.length > 0 && (
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>{t('blaze.recommendedScopes')}</h3>
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t('table.scope')}</th>
                  <th>{t('table.description')}</th>
                  <th>{t('table.recommended')}</th>
                </tr>
              </thead>
              <tbody>
                {setup.recommendedScopes.map((s, i) => (
                  <tr key={i}>
                    <td><code className="mono">{s.scope}</code></td>
                    <td>{s.description}</td>
                    <td>
                      <Badge variant={s.recommended ? 'success' : 'neutral'}>{s.recommended ? t('common.recommended') : t('common.optional')}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Layout>
  );
}
