import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { addToast } from '../components/Toast';
import { t, getLocale } from '../i18n';
import {
  closeGiveaway,
  createGiveaway,
  drawGiveaway,
  enterGiveaway,
  getGiveawayResults,
  getGiveawayStats,
  getGiveaways,
  openGiveaway,
} from '../api/client';
import { Giveaway, GiveawayResultsResponse, GiveawayStatus } from '../api/types';
import { Crown, Gift, Play, Plus, Shuffle, Trophy, Users, X } from 'lucide-react';

const statusColors: Record<GiveawayStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  DRAFT: 'neutral',
  OPEN: 'success',
  CLOSED: 'warning',
  DRAWING: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'error',
};

const statusLabels: Record<GiveawayStatus, string> = {
  DRAFT: t('giveaways.statusDraft'),
  OPEN: t('giveaways.statusOpen'),
  CLOSED: t('giveaways.statusClosed'),
  DRAWING: t('giveaways.statusDrawing'),
  COMPLETED: t('giveaways.statusCompleted'),
  CANCELLED: t('giveaways.statusCancelled'),
};

export default function Giveaways() {
  const fetchGiveaways = useCallback(() => getGiveaways(), []);
  const fetchStats = useCallback(() => getGiveawayStats(), []);
  const { data: giveaways, loading, error: giveawaysError, reload: reloadGiveaways } = usePolling(fetchGiveaways, 12000);
  const { data: stats, error: statsError, reload: reloadStats } = usePolling(fetchStats, 15000);

  const pollError = giveawaysError || statsError;
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedGiveaway, setSelectedGiveaway] = useState<Giveaway | null>(null);
  const [selectedResults, setSelectedResults] = useState<GiveawayResultsResponse | null>(null);
  const [participantName, setParticipantName] = useState('');
  const [createForm, setCreateForm] = useState({ title: '', description: '', maxEntries: 100 });

  const reloadAll = async () => {
    await Promise.all([reloadGiveaways(), reloadStats()]);
  };

  const runAction = async (action: () => Promise<unknown>, success: string, actionKey?: string) => {
    if (actionKey) setActionLoading(actionKey);
    try {
      await action();
      addToast('success', success);
      await reloadAll();
      if (selectedGiveaway) {
        const refreshed = await getGiveawayResults(selectedGiveaway.id).catch(() => null);
        setSelectedResults(refreshed);
      }
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('giveaways.actionError'));
    } finally {
      if (actionKey) setActionLoading(null);
    }
  };

  const createNewGiveaway = async () => {
    setActionLoading('create');
    try {
      await createGiveaway({
        title: createForm.title.trim(),
        description: createForm.description.trim(),
        maxEntries: createForm.maxEntries,
      });
      addToast('success', t('giveaways.createSuccess'));
      setShowCreateModal(false);
      setCreateForm({ title: '', description: '', maxEntries: 100 });
      await reloadAll();
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('giveaways.createError'));
    } finally {
      setActionLoading(null);
    }
  };

  const openDetails = async (giveaway: Giveaway) => {
    setSelectedGiveaway(giveaway);
    const results = await getGiveawayResults(giveaway.id).catch(() => null);
    setSelectedResults(results);
  };

  const visibleGiveaways = giveaways || [];

  const giveawayColumns: Column<Giveaway>[] = [
    { key: 'title', header: t('common.name'), sortable: true },
    { key: 'status', header: t('common.status'), render: (g) => <Badge variant={statusColors[g.status]} dot>{statusLabels[g.status]}</Badge> },
    { key: 'entryCount', header: t('giveaways.participants'), sortable: true },
    { key: 'maxEntries', header: t('common.limit') },
    {
      key: 'createdAt', header: t('giveaways.colCreated'),
      render: (g) => <span className="mono text-xs">{new Date(g.createdAt).toLocaleString(getLocale())}</span>,
    },
    {
      key: 'actions', header: '', width: 220,
      render: (g) => (
        <div className="flex gap-xs flex-wrap">
          {g.status === 'DRAFT' && (
            <button className="btn btn-secondary btn-sm btn-icon" aria-label={`${t('giveaways.openGiveaway')} ${g.title}`} disabled={actionLoading !== null} onClick={() => runAction(() => openGiveaway(g.id), t('giveaways.openSuccess'), 'open')}>
              <Play size={12} />
            </button>
          )}
          {g.status === 'OPEN' && (
            <button className="btn btn-secondary btn-sm btn-icon" aria-label={`${t('giveaways.closeGiveaway')} ${g.title}`} disabled={actionLoading !== null} onClick={() => runAction(() => closeGiveaway(g.id), t('giveaways.closeSuccess'), 'close')}>
              <X size={12} />
            </button>
          )}
          {g.status === 'CLOSED' && (
            <button className="btn btn-accent btn-sm btn-icon" aria-label={`${t('giveaways.drawWinner')} ${g.title}`} disabled={actionLoading !== null} onClick={() => runAction(() => drawGiveaway(g.id, 1), t('giveaways.drawSuccess'), 'draw')}>
              <Shuffle size={12} />
            </button>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => openDetails(g)} disabled={actionLoading !== null}>{t('common.view')}</button>
        </div>
      ),
    },
  ];

  return (
    <Layout title={t('giveaways.title')} subtitle={t('giveaways.subtitle')}>
      {pollError && <ErrorBanner error={pollError} onRetry={reloadAll} />}
      <div className="stats-grid mb-6">
        <StatsCard title={t('giveaways.openGiveaways')} value={stats?.openCount ?? 0} icon={<Gift size={18} />} color="accent" subtitle={`${stats?.totalGiveaways ?? 0} ${t('common.total')}`} />
        <StatsCard title={t('giveaways.participants')} value={stats?.totalEntries ?? 0} icon={<Users size={18} />} color="primary" />
        <StatsCard title={t('giveaways.completed')} value={stats?.completedCount ?? 0} icon={<Trophy size={18} />} color="success" />
      </div>

      <div className="section-header">
        <span className="section-title">{t('giveaways.title')}</span>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)} disabled={actionLoading !== null}>
          <Plus size={14} />
          {t('giveaways.newGiveaway')}
        </button>
      </div>

      {loading && !giveaways ? <div className="skeleton-list" /> : (
        <DataTable columns={giveawayColumns} data={visibleGiveaways} filterable filterKeys={['title', 'status']} emptyMessage={t('giveaways.empty')} />
      )}

      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title={t('giveaways.newGiveaway')}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)} disabled={actionLoading !== null}>{t('common.cancel')}</button>
            <button className="btn btn-accent" onClick={createNewGiveaway} disabled={!createForm.title.trim() || actionLoading !== null}>
              {actionLoading === 'create' ? t('giveaways.creating') : t('giveaways.createGiveaway')}
            </button>
          </>
        }
      >
        <div>
          <label htmlFor="giveaway-title">{t('giveaways.giveawayNameLabel')}</label>
          <input id="giveaway-title" className="input" value={createForm.title} onChange={(event) => setCreateForm({ ...createForm, title: event.target.value })} placeholder={t('giveaways.giveawayNamePlaceholder')} />
        </div>
        <div>
          <label htmlFor="giveaway-description">{t('giveaways.descriptionLabel')}</label>
          <input id="giveaway-description" className="input" value={createForm.description} onChange={(event) => setCreateForm({ ...createForm, description: event.target.value })} placeholder={t('common.optional')} />
        </div>
        <div>
          <label htmlFor="giveaway-max">{t('giveaways.maxEntriesLabel')}</label>
          <input id="giveaway-max" className="input" type="number" min={1} value={createForm.maxEntries} onChange={(event) => setCreateForm({ ...createForm, maxEntries: Number(event.target.value) })} />
        </div>
      </Modal>

      <Modal
        open={!!selectedGiveaway}
        onClose={() => {
          setSelectedGiveaway(null);
          setSelectedResults(null);
          setParticipantName('');
        }}
        title={selectedGiveaway?.title || ''}
        footer={<button className="btn btn-secondary" onClick={() => setSelectedGiveaway(null)} disabled={actionLoading !== null}>{t('common.close')}</button>}
      >
        {selectedGiveaway && (
          <>
            <div className="flex items-center justify-between flex-wrap gap-sm">
              <Badge variant={statusColors[selectedGiveaway.status]} dot>{statusLabels[selectedGiveaway.status]}</Badge>
              <span className="text-text-secondary text-[13px]">
                {selectedGiveaway.entryCount}/{selectedGiveaway.maxEntries} {t('giveaways.participants')}
              </span>
            </div>
            {selectedGiveaway.status === 'OPEN' && (
              <div>
                <label htmlFor="participant-name">{t('giveaways.addParticipantLabel')}</label>
                <div className="flex gap-sm">
                  <input id="participant-name" className="input" value={participantName} onChange={(event) => setParticipantName(event.target.value)} placeholder={t('giveaways.participantNamePlaceholder')} />
                  <button
                    className="btn btn-primary"
                    disabled={!participantName.trim() || actionLoading !== null}
                    onClick={() => runAction(async () => {
                      await enterGiveaway(selectedGiveaway.id, participantName.trim());
                      setParticipantName('');
                    }, t('giveaways.enterSuccess'), 'enter')}
                  >
                    {actionLoading === 'enter' ? t('giveaways.entering') : t('giveaways.enterButton')}
                  </button>
                </div>
              </div>
            )}
            <div>
              <div className="section-title mb-2">{t('giveaways.winners')}</div>
              {selectedResults?.winners.length ? (
                <div className="log-panel">
                  {selectedResults.winners.map((winner) => (
                    <div key={winner.entryId} className="log-line">
                      <Crown size={14} className="text-accent" />
                      <span>{winner.participantName}</span>
                      <span className="timestamp">{new Date(winner.enteredAt).toLocaleString(getLocale())}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-state p-6">{t('giveaways.noWinners')}</div>
              )}
            </div>
          </>
        )}
      </Modal>
    </Layout>
  );
}
