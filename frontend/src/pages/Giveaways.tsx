import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { addToast } from '../components/Toast';
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
  DRAFT: 'Rascunho',
  OPEN: 'Aberto',
  CLOSED: 'Fechado',
  DRAWING: 'Sorteando',
  COMPLETED: 'Finalizado',
  CANCELLED: 'Cancelado',
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
      addToast('error', error instanceof Error ? error.message : 'Erro ao executar acao');
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
      addToast('success', 'Sorteio criado');
      setShowCreateModal(false);
      setCreateForm({ title: '', description: '', maxEntries: 100 });
      await reloadAll();
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : 'Erro ao criar sorteio');
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
    { key: 'title', header: 'Nome', sortable: true },
    { key: 'status', header: 'Status', render: (g) => <Badge variant={statusColors[g.status]} dot>{statusLabels[g.status]}</Badge> },
    { key: 'entryCount', header: 'Participantes', sortable: true },
    { key: 'maxEntries', header: 'Limite' },
    {
      key: 'createdAt', header: 'Criado em',
      render: (g) => <span className="mono" style={{ fontSize: 12 }}>{new Date(g.createdAt).toLocaleString('pt-BR')}</span>,
    },
    {
      key: 'actions', header: '', width: 220,
      render: (g) => (
        <div className="flex gap-xs flex-wrap">
          {g.status === 'DRAFT' && (
            <button className="btn btn-secondary btn-sm btn-icon" aria-label={`Abrir sorteio ${g.title}`} disabled={actionLoading !== null} onClick={() => runAction(() => openGiveaway(g.id), 'Sorteio aberto', 'open')}>
              <Play size={12} />
            </button>
          )}
          {g.status === 'OPEN' && (
            <button className="btn btn-secondary btn-sm btn-icon" aria-label={`Fechar sorteio ${g.title}`} disabled={actionLoading !== null} onClick={() => runAction(() => closeGiveaway(g.id), 'Sorteio fechado', 'close')}>
              <X size={12} />
            </button>
          )}
          {g.status === 'CLOSED' && (
            <button className="btn btn-accent btn-sm btn-icon" aria-label={`Sortear ganhador de ${g.title}`} disabled={actionLoading !== null} onClick={() => runAction(() => drawGiveaway(g.id, 1), 'Sorteio realizado', 'draw')}>
              <Shuffle size={12} />
            </button>
          )}
          <button className="btn btn-secondary btn-sm" onClick={() => openDetails(g)} disabled={actionLoading !== null}>Ver</button>
        </div>
      ),
    },
  ];

  return (
    <Layout title="Sorteios" subtitle="Gerenciar sorteios e participantes via API real">
      {pollError && <ErrorBanner error={pollError} onRetry={reloadAll} />}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard title="Sorteios Abertos" value={stats?.openCount ?? 0} icon={<Gift size={18} />} color="accent" subtitle={`${stats?.totalGiveaways ?? 0} total`} />
        <StatsCard title="Participantes" value={stats?.totalEntries ?? 0} icon={<Users size={18} />} color="primary" />
        <StatsCard title="Finalizados" value={stats?.completedCount ?? 0} icon={<Trophy size={18} />} color="success" />
      </div>

      <div className="section-header">
        <span className="section-title">Sorteios</span>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)} disabled={actionLoading !== null}>
          <Plus size={14} />
          Novo Sorteio
        </button>
      </div>

      {loading && !giveaways ? <div className="skeleton-list" /> : (
        <DataTable columns={giveawayColumns} data={visibleGiveaways} filterable filterKeys={['title', 'status']} emptyMessage="Nenhum sorteio criado." />
      )}

      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Novo Sorteio"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)} disabled={actionLoading !== null}>Cancelar</button>
            <button className="btn btn-accent" onClick={createNewGiveaway} disabled={!createForm.title.trim() || actionLoading !== null}>
              {actionLoading === 'create' ? 'Criando...' : 'Criar Sorteio'}
            </button>
          </>
        }
      >
        <div>
          <label htmlFor="giveaway-title">Nome do Sorteio</label>
          <input id="giveaway-title" className="input" value={createForm.title} onChange={(event) => setCreateForm({ ...createForm, title: event.target.value })} placeholder="Ex: Sorteio de Inscritos" />
        </div>
        <div>
          <label htmlFor="giveaway-description">Descricao</label>
          <input id="giveaway-description" className="input" value={createForm.description} onChange={(event) => setCreateForm({ ...createForm, description: event.target.value })} placeholder="Opcional" />
        </div>
        <div>
          <label htmlFor="giveaway-max">Limite de participantes</label>
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
        footer={<button className="btn btn-secondary" onClick={() => setSelectedGiveaway(null)} disabled={actionLoading !== null}>Fechar</button>}
      >
        {selectedGiveaway && (
          <>
            <div className="flex items-center justify-between flex-wrap gap-sm">
              <Badge variant={statusColors[selectedGiveaway.status]} dot>{statusLabels[selectedGiveaway.status]}</Badge>
              <span style={{ color: 'var(--text-secondary)', fontSize: 13 }}>
                {selectedGiveaway.entryCount}/{selectedGiveaway.maxEntries} participantes
              </span>
            </div>
            {selectedGiveaway.status === 'OPEN' && (
              <div>
                <label htmlFor="participant-name">Adicionar participante</label>
                <div className="flex gap-sm">
                  <input id="participant-name" className="input" value={participantName} onChange={(event) => setParticipantName(event.target.value)} placeholder="Nome do participante" />
                  <button
                    className="btn btn-primary"
                    disabled={!participantName.trim() || actionLoading !== null}
                    onClick={() => runAction(async () => {
                      await enterGiveaway(selectedGiveaway.id, participantName.trim());
                      setParticipantName('');
                    }, 'Participante adicionado', 'enter')}
                  >
                    {actionLoading === 'enter' ? 'Entrando...' : 'Entrar'}
                  </button>
                </div>
              </div>
            )}
            <div>
              <div className="section-title" style={{ marginBottom: 8 }}>Ganhadores</div>
              {selectedResults?.winners.length ? (
                <div className="log-panel">
                  {selectedResults.winners.map((winner) => (
                    <div key={winner.entryId} className="log-line">
                      <Crown size={14} style={{ color: 'var(--accent)' }} />
                      <span>{winner.participantName}</span>
                      <span className="timestamp">{new Date(winner.enteredAt).toLocaleString('pt-BR')}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-state" style={{ padding: 24 }}>Nenhum ganhador sorteado ainda.</div>
              )}
            </div>
          </>
        )}
      </Modal>
    </Layout>
  );
}
