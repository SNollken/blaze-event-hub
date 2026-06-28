import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge, StatusDot } from '../components/Badge';
import { usePolling, addToast } from '../components/Toast';
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
  User,
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

  const { data: status, reload: reloadStatus } = usePolling(fetchStatus, 12000);
  const { data: setup, reload: reloadSetup } = usePolling(fetchSetup, 15000);
  const { data: oauth, reload: reloadOAuth } = usePolling(fetchOAuth, 12000);

  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const handleStartOAuth = async () => {
    setActionLoading('oauth-start');
    try {
      const res = await startOAuth();
      window.open(res.authorizationUrl, '_blank', 'noopener,noreferrer');
      addToast('success', 'Pagina de autenticacao aberta');
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : 'Erro ao iniciar OAuth');
    } finally {
      setActionLoading(null);
    }
  };

  const handleRefresh = async () => {
    setActionLoading('refresh');
    try {
      await refreshOAuth();
      addToast('success', 'Sessao renovada com sucesso');
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : 'Erro ao renovar sessao');
    } finally {
      setActionLoading(null);
    }
  };

  const handleDisconnect = async () => {
    setActionLoading('disconnect');
    try {
      await disconnectOAuth();
      addToast('success', 'Conta desconectada');
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : 'Erro ao desconectar');
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
      addToast('success', 'Status atualizado');
    } finally {
      setActionLoading(null);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    addToast('success', 'Copiado para area de transferencia');
  };

  const isOAuthReady = setup?.oauthStartReady;
  const isConnected = oauth?.connected;
  const isTokenPresent = oauth?.tokenPresent;

  return (
    <Layout
      title="Blaze Channel"
      subtitle="Configuracao da integracao com a Blaze"
      headerActions={
        <button className="btn btn-secondary btn-sm" onClick={handleSync} disabled={actionLoading === 'sync'}>
          <RefreshCw size={14} className={actionLoading === 'sync' ? 'spin' : ''} />
          Sincronizar
        </button>
      }
    >
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Conexao Blaze"
          value={isConnected ? 'Conectado' : 'Desconectado'}
          icon={<Key size={18} />}
          color={isConnected ? 'success' : 'warning'}
          subtitle={oauth?.profile?.displayName || 'Sem conta'}
        />
        <StatsCard
          title="Token"
          value={isTokenPresent ? 'Presente' : 'Ausente'}
          icon={<Shield size={18} />}
          color={isTokenPresent ? 'success' : 'error'}
          subtitle={oauth?.tokenExpiredOrUnknown ? 'Expirado' : 'Valido'}
        />
        <StatsCard
          title="Events Config"
          value={status?.socketConfigured ? 'Pronto' : 'Nao configurado'}
          icon={<Radio size={18} />}
          color={status?.socketConfigured ? 'success' : 'neutral'}
        />
        <StatsCard
          title="Canal Monitorado"
          value={status?.monitoredChannelConfigured ? 'Configurado' : 'Nao configurado'}
          icon={<Radio size={18} />}
          color={status?.monitoredChannelConfigured ? 'success' : 'neutral'}
          subtitle={setup?.monitoredChannel || ''}
        />
      </div>

      {/* Setup checklist */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 24 }}>
        <div className="section-header" style={{ marginBottom: 16 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600 }}>Checklist de Configuracao</h3>
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
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Autenticacao OAuth</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {!isConnected ? (
              <button
                className="btn btn-accent"
                onClick={handleStartOAuth}
                disabled={!isOAuthReady || actionLoading === 'oauth-start'}
              >
                <Key size={14} />
                {actionLoading === 'oauth-start' ? 'Abrindo...' : 'Conectar com a Blaze'}
              </button>
            ) : (
              <>
                <button className="btn btn-secondary" onClick={handleRefresh} disabled={actionLoading === 'refresh'}>
                  <RefreshCw size={14} />
                  {actionLoading === 'refresh' ? 'Renovando...' : 'Renovar Sessao'}
                </button>
                <button className="btn btn-danger" onClick={handleDisconnect} disabled={actionLoading === 'disconnect'}>
                  <XCircle size={14} />
                  {actionLoading === 'disconnect' ? 'Desconectando...' : 'Desconectar Conta'}
                </button>
              </>
            )}

            {setup?.nextSteps && setup.nextSteps.length > 0 && (
              <div style={{ marginTop: 8 }}>
                <div style={{ fontSize: 12, fontWeight: 500, color: 'var(--text-muted)', marginBottom: 6 }}>
                  Proximos passos:
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
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Conta Conectada</h3>
          {oauth?.profile ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {oauth.profile.avatarUrl && (
                  <img src={oauth.profile.avatarUrl} alt="" style={{ width: 40, height: 40, borderRadius: '50%' }} />
                )}
                <div>
                  <div style={{ fontWeight: 600 }}>{oauth.profile.displayName}</div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>@{oauth.profile.username}</div>
                </div>
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                <div>Scopes: {oauth.scopes?.join(', ') || 'nenhum'}</div>
                {oauth.lastProfileSyncAt && (
                  <div>Ultimo sync: {new Date(oauth.lastProfileSyncAt).toLocaleString('pt-BR')}</div>
                )}
              </div>
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              Nenhuma conta conectada. Use o botao ao lado para autenticar.
            </div>
          )}
        </div>
      </div>

      {/* Redirect URI & Docs */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 24 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Redirect URI e Documentacao</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div>
            <label>Redirect URI</label>
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
                {setup?.redirectUri || 'Nao configurado'}
              </code>
              {setup?.redirectUri && (
                <button className="copy-btn" onClick={() => copyToClipboard(setup.redirectUri!)}>
                  <Copy size={12} />
                  Copiar
                </button>
              )}
            </div>
          </div>

          {setup?.docsLinks && setup.docsLinks.length > 0 && (
            <div>
              <label>Links Uteis</label>
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
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Scopes Recomendados</h3>
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Scope</th>
                  <th>Descricao</th>
                  <th>Recomendado</th>
                </tr>
              </thead>
              <tbody>
                {setup.recommendedScopes.map((s, i) => (
                  <tr key={i}>
                    <td><code className="mono">{s.scope}</code></td>
                    <td>{s.description}</td>
                    <td>
                      <Badge variant={s.recommended ? 'success' : 'neutral'}>
                        {s.recommended ? 'Recomendado' : 'Opcional'}
                      </Badge>
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
