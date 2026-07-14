import { useCallback, useState } from 'react';
import { Link2, LogOut, RefreshCw, ShieldCheck, UserRound } from 'lucide-react';
import {
  disconnectOAuth,
  getMe,
  getOAuthSession,
  startOAuth,
} from '../api/client';
import type { MemberProfile, OAuthSessionResponse } from '../api/types';
import { Modal } from '../components/Modal';
import { addToast, usePolling } from '../components/Toast';
import { notifyAuthSessionChanged } from '../auth-session';
import { toSafeOAuthUrl } from '../oauth-navigation';

interface ConnectionState {
  session: OAuthSessionResponse;
  profile: MemberProfile | null;
}

function formatExpiry(value: string | null | undefined): string {
  if (!value) return 'Não informada';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Não informada';
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export default function StudioChannel() {
  const fetchConnection = useCallback(async (): Promise<ConnectionState> => {
    const session = await getOAuthSession();
    if (!session.connected) return { session, profile: null };
    try {
      return { session, profile: await getMe() };
    } catch {
      return { session, profile: null };
    }
  }, []);
  const connection = usePolling(fetchConnection, 15_000);
  const [action, setAction] = useState<'connect' | 'disconnect' | null>(null);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);

  const connect = async () => {
    setAction('connect');
    try {
      const response = await startOAuth();
      window.location.assign(toSafeOAuthUrl(response.authorizationUrl));
    } catch (connectError) {
      addToast('error', connectError instanceof Error ? connectError.message : 'Não foi possível iniciar a conexão com a Blaze.');
      setAction(null);
    }
  };

  const disconnect = async () => {
    setAction('disconnect');
    try {
      await disconnectOAuth();
      setConfirmDisconnect(false);
      notifyAuthSessionChanged();
      await connection.reload();
      addToast('success', 'Conta Blaze desconectada do hub.');
    } catch (disconnectError) {
      addToast('error', disconnectError instanceof Error ? disconnectError.message : 'Não foi possível desconectar a conta.');
    } finally {
      setAction(null);
    }
  };

  const connected = connection.data?.session.connected === true;
  const profile = connection.data?.profile;
  const sessionProfile = connection.data?.session.profile;
  const displayName = profile?.displayName || sessionProfile?.displayName || sessionProfile?.username || 'Criador Blaze';
  const username = profile?.blazeUsername || sessionProfile?.username || '';
  const avatarUrl = profile?.avatarUrl || sessionProfile?.avatarUrl || null;
  const connectionUnavailable = Boolean(connection.error && !connection.data);

  return (
    <div className="hub-page">
      <header className="page-hero">
        <div>
          <span className="section-label">Configuração Blaze</span>
          <h1 className="page-title">Sua conexão com a transmissão</h1>
          <p>O hub usa OAuth para ler as mensagens necessárias durante os giveaways que você abrir.</p>
        </div>
        <button type="button" className="btn btn-secondary" onClick={() => void connection.reload()} disabled={connection.loading || action !== null}>
          <RefreshCw size={16} aria-hidden="true" /> Atualizar estado
        </button>
      </header>

      {connection.error && <div className="notice notice-danger" role="alert">{connection.error}</div>}

      <div className="control-grid">
        <section className="control-card">
          <div className="section-label">Estado da conta</div>
          {connection.loading && !connection.data ? (
            <div className="empty" role="status">Verificando conexão segura...</div>
          ) : connectionUnavailable ? (
            <div className="empty" role="status">O estado da conta não está disponível no momento.</div>
          ) : connected ? (
            <div className="connection-profile" aria-label="Conta Blaze conectada">
              <div className="profile-avatar">
                {avatarUrl ? <img src={avatarUrl} alt="" /> : <UserRound aria-hidden="true" />}
              </div>
              <div className="creator-identity">
                <strong>{displayName}</strong>
                {username && <span className="creator-handle">@{username}</span>}
                <span className="pill pill--open">Conectado</span>
              </div>
            </div>
          ) : (
            <div className="empty">
              <Link2 aria-hidden="true" />
              <strong>Nenhuma conta Blaze conectada</strong>
              <span>Conecte a conta do criador para abrir capturas de chat.</span>
            </div>
          )}

          <div className="form-actions">
            <button type="button" className="btn btn-primary" onClick={() => void connect()} disabled={action !== null}>
              <Link2 size={16} aria-hidden="true" />
              {action === 'connect' ? 'Abrindo Blaze...' : connected ? 'Reconectar conta' : 'Conectar com a Blaze'}
            </button>
            {connected && (
              <button type="button" className="btn btn-danger" onClick={() => setConfirmDisconnect(true)} disabled={action !== null}>
                <LogOut size={16} aria-hidden="true" /> Desconectar
              </button>
            )}
          </div>
        </section>

        <section className="control-card">
          <ShieldCheck size={28} aria-hidden="true" />
          <div className="section-label">Credenciais protegidas</div>
          <h2>O segredo não passa pelo navegador</h2>
          <p>Você nunca precisa informar credenciais sensíveis nesta tela. A autorização acontece diretamente na Blaze e os dados de acesso ficam criptografados no servidor.</p>
          <dl className="event-stats">
            <div>
              <dt>Sessão</dt>
              <dd>{connectionUnavailable ? 'Indisponível' : connected ? 'Ativa' : 'Desconectada'}</dd>
            </div>
            <div>
              <dt>Validade atual</dt>
              <dd>{formatExpiry(connection.data?.session.expiresAt)}</dd>
            </div>
          </dl>
        </section>

        <section className="control-card">
          <div className="section-label">Permissões</div>
          <p>O hub solicita apenas os escopos exibidos abaixo. Eles são usados para identificar sua conta e capturar as entradas no chat dos eventos abertos.</p>
          {(connection.data?.session.scopes || []).length ? (
            <ul className="participant-list">
              {(connection.data?.session.scopes || []).map((scope) => (
                <li key={scope}><ShieldCheck size={16} aria-hidden="true" /><code>{scope}</code></li>
              ))}
            </ul>
          ) : (
            <div className="empty">As permissões aparecerão depois da conexão.</div>
          )}
        </section>
      </div>

      <Modal
        open={confirmDisconnect}
        onClose={() => setConfirmDisconnect(false)}
        title="Desconectar a conta Blaze?"
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmDisconnect(false)} disabled={action !== null}>Manter conectada</button>
            <button type="button" className="btn btn-danger" onClick={() => void disconnect()} disabled={action !== null}>
              <LogOut size={16} aria-hidden="true" /> {action === 'disconnect' ? 'Desconectando...' : 'Desconectar'}
            </button>
          </>
        )}
      >
        <p>Capturas abertas não conseguirão continuar lendo o chat até que uma conta seja conectada novamente.</p>
      </Modal>
    </div>
  );
}
