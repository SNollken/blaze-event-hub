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
import { getUserFacingErrorMessage } from '../errors/user-facing-error';
import { useI18n } from '../i18n/I18nContext';
import { toSafeOAuthUrl } from '../oauth-navigation';

interface ConnectionState {
  session: OAuthSessionResponse;
  profile: MemberProfile | null;
}

function formatExpiry(value: string | null | undefined, locale: string, unavailable: string): string {
  if (!value) return unavailable;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return unavailable;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export default function StudioChannel() {
  const { lang, t } = useI18n();
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
      addToast('error', getUserFacingErrorMessage(connectError, t('studioConnectFallback')));
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
      addToast('success', t('studioDisconnectSuccess'));
    } catch (disconnectError) {
      addToast('error', getUserFacingErrorMessage(disconnectError, t('studioDisconnectFallback')));
    } finally {
      setAction(null);
    }
  };

  const connected = connection.data?.session.connected === true;
  const profile = connection.data?.profile;
  const sessionProfile = connection.data?.session.profile;
  const displayName = profile?.displayName || sessionProfile?.displayName || sessionProfile?.username || t('studioCreatorFallback');
  const username = profile?.blazeUsername || sessionProfile?.username || '';
  const avatarUrl = profile?.avatarUrl || sessionProfile?.avatarUrl || null;
  const connectionUnavailable = Boolean(connection.error && !connection.data);

  return (
    <div className="hub-page">
      <header className="page-hero">
        <div>
          <span className="section-label">{t('studioEyebrow')}</span>
          <h1 className="page-title">{t('studioHeading')}</h1>
          <p>{t('studioSubtitle')}</p>
        </div>
        <button type="button" className="btn btn-secondary" onClick={() => void connection.reload()} disabled={connection.loading || action !== null}>
          <RefreshCw size={16} aria-hidden="true" /> {t('studioRefresh')}
        </button>
      </header>

      {connection.error && connection.data && (
        <div className="notice notice-danger" role="alert">{t('studioUnavailable')}</div>
      )}

      <div className="control-grid">
        <section className="control-card">
          <div className="section-label">{t('studioAccountStatus')}</div>
          {connection.loading && !connection.data ? (
            <div className="empty" role="status">{t('studioChecking')}</div>
          ) : connectionUnavailable ? (
            <div className="empty" role="alert">{t('studioUnavailable')}</div>
          ) : connected ? (
            <div className="connection-profile" aria-label={t('studioConnectedAccountAria')}>
              <div className="profile-avatar">
                {avatarUrl ? <img src={avatarUrl} alt="" /> : <UserRound aria-hidden="true" />}
              </div>
              <div className="creator-identity">
                <strong>{displayName}</strong>
                {username && <span className="creator-handle">@{username}</span>}
                <span className="pill pill--open">{t('studioConnected')}</span>
              </div>
            </div>
          ) : (
            <div className="empty">
              <Link2 aria-hidden="true" />
              <strong>{t('studioNoAccount')}</strong>
              <span>{t('studioNoAccountDescription')}</span>
            </div>
          )}

          <div className="form-actions">
            <button type="button" className="btn btn-primary" onClick={() => void connect()} disabled={action !== null}>
              <Link2 size={16} aria-hidden="true" />
              {action === 'connect' ? t('studioOpeningBlaze') : connected ? t('studioReconnect') : t('studioConnect')}
            </button>
            {connected && (
              <button type="button" className="btn btn-danger" onClick={() => setConfirmDisconnect(true)} disabled={action !== null}>
                <LogOut size={16} aria-hidden="true" /> {t('studioDisconnect')}
              </button>
            )}
          </div>
        </section>

        <section className="control-card">
          <ShieldCheck size={28} aria-hidden="true" />
          <div className="section-label">{t('studioProtectedCredentials')}</div>
          <h2>{t('studioSecretHeading')}</h2>
          <p>{t('studioSecretDescription')}</p>
          <dl className="event-stats">
            <div>
              <dt>{t('studioSession')}</dt>
              <dd>{connectionUnavailable ? t('studioStateUnavailable') : connected ? t('studioStateActive') : t('studioStateDisconnected')}</dd>
            </div>
            <div>
              <dt>{t('studioCurrentExpiry')}</dt>
              <dd>{formatExpiry(connection.data?.session.expiresAt, lang, t('studioExpiryUnavailable'))}</dd>
            </div>
          </dl>
        </section>

        <section className="control-card">
          <div className="section-label">{t('studioPermissions')}</div>
          <p>{t('studioPermissionsDescription')}</p>
          {(connection.data?.session.scopes || []).length ? (
            <ul className="participant-list">
              {(connection.data?.session.scopes || []).map((scope) => (
                <li key={scope}><ShieldCheck size={16} aria-hidden="true" /><code>{scope}</code></li>
              ))}
            </ul>
          ) : (
            <div className="empty">{t('studioPermissionsEmpty')}</div>
          )}
        </section>
      </div>

      <Modal
        open={confirmDisconnect}
        onClose={() => setConfirmDisconnect(false)}
        title={t('studioDisconnectTitle')}
        footer={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmDisconnect(false)} disabled={action !== null}>{t('studioKeepConnected')}</button>
            <button type="button" className="btn btn-danger" onClick={() => void disconnect()} disabled={action !== null}>
              <LogOut size={16} aria-hidden="true" /> {action === 'disconnect' ? t('studioDisconnecting') : t('studioDisconnect')}
            </button>
          </>
        )}
      >
        <p>{t('studioDisconnectWarning')}</p>
      </Modal>
    </div>
  );
}
