import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { addToast } from '../components/Toast';
import { t } from '../i18n';
import {
  createGiveaway,
  enterGiveaway,
  getGiveaways,
  getStatus,
  startOAuth,
} from '../api/client';
import { Giveaway, GiveawayStatus } from '../api/types';
import { ArrowRight, KeyRound, Plus, Users } from 'lucide-react';

const statusColors: Record<GiveawayStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  DRAFT: 'neutral',
  OPEN: 'success',
  CLOSED: 'warning',
  DRAWING: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'error',
};

export default function Dashboard() {
  const fetchGiveaways = useCallback(() => getGiveaways(), []);
  const fetchStatus = useCallback(() => getStatus(), []);

  const { data: giveaways, loading, error: giveawaysError, reload: reloadGiveaways } = usePolling(fetchGiveaways, 15000);
  const { data: status, error: statusError, reload: reloadStatus } = usePolling(fetchStatus, 20000);

  const pollError = giveawaysError || statusError;

  const [participantName, setParticipantName] = useState('');
  const [joiningId, setJoiningId] = useState<string | null>(null);
  const [connecting, setConnecting] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createForm, setCreateForm] = useState({ title: '', description: '', maxEntries: 100 });

  const statusLabels: Record<GiveawayStatus, string> = {
    DRAFT: t('giveaways.statusDraft'),
    OPEN: t('giveaways.statusOpen'),
    CLOSED: t('giveaways.statusClosed'),
    DRAWING: t('giveaways.statusDrawing'),
    COMPLETED: t('giveaways.statusCompleted'),
    CANCELLED: t('giveaways.statusCancelled'),
  };

  const allGiveaways = giveaways || [];
  const openGiveaways = allGiveaways.filter((g) => g.status === 'OPEN');
  const recentGiveaways = [...allGiveaways]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);

  const handleConnect = async () => {
    setConnecting(true);
    try {
      const res = await startOAuth();
      window.open(res.authorizationUrl, '_blank', 'noopener,noreferrer');
      addToast('success', t('blaze.connectSuccess'));
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('blaze.connectError'));
    } finally {
      setConnecting(false);
    }
  };

  const handleJoin = async (giveaway: Giveaway) => {
    const name = participantName.trim();
    if (!name) return;
    setJoiningId(giveaway.id);
    try {
      await enterGiveaway(giveaway.id, name);
      addToast('success', t('home.joinSuccess'));
      await reloadGiveaways();
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('home.joinError'));
    } finally {
      setJoiningId(null);
    }
  };

  const handleCreate = async () => {
    setCreating(true);
    try {
      await createGiveaway({
        title: createForm.title.trim(),
        description: createForm.description.trim(),
        maxEntries: createForm.maxEntries,
      });
      addToast('success', t('home.createSuccess'));
      setShowCreateModal(false);
      setCreateForm({ title: '', description: '', maxEntries: 100 });
      await reloadGiveaways();
    } catch (e: unknown) {
      addToast('error', e instanceof Error ? e.message : t('home.createError'));
    } finally {
      setCreating(false);
    }
  };

  if (loading && !giveaways) {
    return (
      <Layout title={t('home.title')} subtitle={t('home.subtitle')}>
        <div className="skeleton-list" />
      </Layout>
    );
  }

  return (
    <Layout title={t('home.title')} subtitle={t('home.subtitle')}>
      {pollError && <ErrorBanner error={pollError} onRetry={() => { reloadGiveaways(); reloadStatus(); }} />}

      {/* Hero: product value + primary actions */}
      <div className="glass-card p-6 mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="max-w-[560px]">
          <h2 className="text-xl font-bold text-text-primary mb-1.5">{t('home.heroTitle')}</h2>
          <p className="text-[13px] text-text-secondary leading-relaxed">{t('home.heroSubtitle')}</p>
        </div>
        <div className="flex items-center gap-2.5 flex-wrap">
          {!status?.oauthConnected && (
            <button className="btn btn-secondary" onClick={handleConnect} disabled={connecting}>
              <KeyRound size={14} />
              {connecting ? t('home.connecting') : t('home.connectBlaze')}
            </button>
          )}
          <button className="btn btn-primary" onClick={() => setShowCreateModal(true)} disabled={creating}>
            <Plus size={14} />
            {t('home.createGiveaway')}
          </button>
        </div>
      </div>

      <div className="responsive-grid-2 mb-6">
        {/* Join: open giveaways from the community */}
        <div className="glass-card p-5">
          <div className="section-header mb-3">
            <span className="section-title">{t('home.openGiveaways')}</span>
            <Badge variant="success">{openGiveaways.length}</Badge>
          </div>

          {openGiveaways.length === 0 ? (
            <div className="empty-state py-8">
              <span>{t('home.noOpenGiveaways')}</span>
              <button className="btn btn-secondary btn-sm mt-2" onClick={() => setShowCreateModal(true)} disabled={creating}>
                <Plus size={13} />
                {t('home.createFirst')}
              </button>
            </div>
          ) : (
            <>
              <div className="mb-3">
                <label htmlFor="home-participant-name">{t('home.yourName')}</label>
                <input
                  id="home-participant-name"
                  className="input"
                  value={participantName}
                  onChange={(e) => setParticipantName(e.target.value)}
                  placeholder={t('home.yourNamePlaceholder')}
                />
              </div>
              <div className="flex flex-col gap-2">
                {openGiveaways.map((g) => (
                  <div
                    key={g.id}
                    className="flex items-center justify-between gap-3 p-3 bg-bg-base border border-border-subtle rounded-lg"
                  >
                    <div className="min-w-0">
                      <div className="text-[13px] font-semibold text-text-primary truncate">{g.title}</div>
                      {g.description && (
                        <div className="text-[11px] text-text-muted truncate">{g.description}</div>
                      )}
                      <div className="text-[11px] text-text-muted flex items-center gap-1 mt-0.5">
                        <Users size={11} aria-hidden="true" />
                        {g.entryCount}/{g.maxEntries} {t('home.participants')}
                      </div>
                    </div>
                    <button
                      className="btn btn-accent btn-sm shrink-0"
                      onClick={() => handleJoin(g)}
                      disabled={!participantName.trim() || joiningId !== null}
                    >
                      {joiningId === g.id ? t('home.joining') : t('home.join')}
                    </button>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {/* Right column: account + own giveaways */}
        <div className="flex flex-col gap-4">
          <div className="glass-card p-5">
            <div className="section-header mb-2">
              <span className="section-title">{t('home.blazeAccount')}</span>
              <Badge variant={status?.oauthConnected ? 'success' : 'neutral'} dot>
                {status?.oauthConnected ? t('common.connected') : t('common.disconnected')}
              </Badge>
            </div>
            {status?.oauthConnected ? (
              <div className="text-[13px] text-text-primary">
                {status.connectedAccountDisplayName || t('common.unknown')}
              </div>
            ) : (
              <>
                <p className="text-[12px] text-text-muted mb-3">{t('home.blazeAccountHint')}</p>
                <button className="btn btn-secondary btn-sm" onClick={handleConnect} disabled={connecting}>
                  <KeyRound size={13} />
                  {connecting ? t('home.connecting') : t('home.connectBlaze')}
                </button>
              </>
            )}
          </div>

          <div className="glass-card p-5 flex-1">
            <div className="section-header mb-3">
              <span className="section-title">{t('home.recentGiveaways')}</span>
              <Link to="/giveaways" className="btn btn-ghost btn-sm no-underline" aria-label={t('home.viewAll')}>
                {t('home.viewAll')}
                <ArrowRight size={12} aria-hidden="true" />
              </Link>
            </div>
            {recentGiveaways.length === 0 ? (
              <div className="empty-state py-6">{t('home.noGiveawaysYet')}</div>
            ) : (
              <div className="flex flex-col gap-2.5">
                {recentGiveaways.map((g) => (
                  <div key={g.id} className="flex items-center justify-between gap-2">
                    <span className="text-[13px] text-text-primary truncate">{g.title}</span>
                    <Badge variant={statusColors[g.status]}>{statusLabels[g.status]}</Badge>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Create giveaway modal */}
      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title={t('home.createTitle')}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)} disabled={creating}>
              {t('common.cancel')}
            </button>
            <button className="btn btn-primary" onClick={handleCreate} disabled={!createForm.title.trim() || creating}>
              {creating ? t('home.creating') : t('home.createGiveaway')}
            </button>
          </>
        }
      >
        <div>
          <label htmlFor="home-giveaway-title">{t('home.giveawayName')}</label>
          <input
            id="home-giveaway-title"
            className="input"
            value={createForm.title}
            onChange={(e) => setCreateForm({ ...createForm, title: e.target.value })}
            placeholder={t('home.giveawayNamePlaceholder')}
          />
        </div>
        <div>
          <label htmlFor="home-giveaway-description">{t('home.description')}</label>
          <input
            id="home-giveaway-description"
            className="input"
            value={createForm.description}
            onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
            placeholder={t('common.optional')}
          />
        </div>
        <div>
          <label htmlFor="home-giveaway-max">{t('home.maxEntries')}</label>
          <input
            id="home-giveaway-max"
            className="input"
            type="number"
            min={1}
            value={createForm.maxEntries}
            onChange={(e) => setCreateForm({ ...createForm, maxEntries: Number(e.target.value) })}
          />
        </div>
      </Modal>
    </Layout>
  );
}
