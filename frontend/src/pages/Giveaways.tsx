import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { usePolling, addToast } from '../components/Toast';
import { getStatus } from '../api/client';
import {
  Gift,
  Users,
  Trophy,
  Plus,
  Trash2,
  Shuffle,
  Clock,
  Crown,
} from 'lucide-react';

interface Giveaway {
  id: string;
  name: string;
  status: 'active' | 'completed' | 'cancelled';
  entries: number;
  winners: number;
  createdAt: string;
  drawAt: string | null;
  winnerName?: string;
}

interface GiveawayEntry {
  id: string;
  giveawayId: string;
  username: string;
  enteredAt: string;
  isWinner: boolean;
}

const demoGiveaways: Giveaway[] = [
  { id: '1', name: 'Sorteio de Inscritos', status: 'active', entries: 42, winners: 0, createdAt: '2024-01-15T10:00:00', drawAt: '2024-01-15T12:00:00' },
  { id: '2', name: 'Giveaway NollenBlaze', status: 'completed', entries: 128, winners: 3, createdAt: '2024-01-14T18:00:00', drawAt: '2024-01-14T20:00:00', winnerName: 'PlayerXPTO' },
  { id: '3', name: 'Sorteio Rapido', status: 'cancelled', entries: 5, winners: 0, createdAt: '2024-01-13T15:00:00', drawAt: null },
];

const demoEntries: GiveawayEntry[] = [
  { id: '1', giveawayId: '1', username: 'user_br_01', enteredAt: '2024-01-15T10:05:00', isWinner: false },
  { id: '2', giveawayId: '1', username: 'player_alpha', enteredAt: '2024-01-15T10:08:00', isWinner: false },
  { id: '3', giveawayId: '1', username: 'gamer_pro_br', enteredAt: '2024-01-15T10:12:00', isWinner: false },
  { id: '4', giveawayId: '2', username: 'PlayerXPTO', enteredAt: '2024-01-14T18:02:00', isWinner: true },
];

const statusColors: Record<string, 'success' | 'warning' | 'error' | 'neutral'> = {
  active: 'success',
  completed: 'success',
  cancelled: 'error',
};

const statusLabels: Record<string, string> = {
  active: 'Ativo',
  completed: 'Finalizado',
  cancelled: 'Cancelado',
};

export default function Giveaways() {
  const fetchStatus = useCallback(() => getStatus(), []);
  const { data: status } = usePolling(fetchStatus, 15000);

  const [giveaways, setGiveaways] = useState<Giveaway[]>(demoGiveaways);
  const [entries] = useState<GiveawayEntry[]>(demoEntries);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedGiveaway, setSelectedGiveaway] = useState<Giveaway | null>(null);

  const activeCount = giveaways.filter((g) => g.status === 'active').length;
  const totalEntries = giveaways.reduce((acc, g) => acc + g.entries, 0);
  const totalWinners = giveaways.reduce((acc, g) => acc + g.winners, 0);

  const giveawayColumns: Column<Giveaway>[] = [
    { key: 'name', header: 'Nome', sortable: true },
    {
      key: 'status', header: 'Status',
      render: (g) => <Badge variant={statusColors[g.status]} dot>{statusLabels[g.status]}</Badge>,
    },
    { key: 'entries', header: 'Participantes', sortable: true },
    { key: 'winners', header: 'Ganhadores' },
    {
      key: 'createdAt', header: 'Criado em',
      render: (g) => <span className="mono" style={{ fontSize: 12 }}>{new Date(g.createdAt).toLocaleString('pt-BR')}</span>,
    },
    {
      key: 'actions', header: '', width: 120,
      render: (g) => (
        <div style={{ display: 'flex', gap: 4 }}>
          {g.status === 'active' && (
            <button
              className="btn btn-accent btn-sm"
              onClick={() => {
                const updated = giveaways.map((x) =>
                  x.id === g.id ? { ...x, status: 'completed' as const, winners: 1, winnerName: 'Sorteado!' } : x
                );
                setGiveaways(updated);
                addToast('success', 'Sorteio realizado!');
              }}
            >
              <Shuffle size={12} />
            </button>
          )}
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => setSelectedGiveaway(g)}
          >
            Ver
          </button>
        </div>
      ),
    },
  ];

  const entryColumns: Column<GiveawayEntry>[] = [
    { key: 'username', header: 'Usuario', sortable: true },
    {
      key: 'isWinner', header: 'Status',
      render: (e) => e.isWinner ? (
        <Badge variant="success"><Crown size={10} /> Ganhador</Badge>
      ) : (
        <Badge variant="neutral">Participante</Badge>
      ),
    },
    {
      key: 'enteredAt', header: 'Entrou em',
      render: (e) => <span className="mono" style={{ fontSize: 12 }}>{new Date(e.enteredAt).toLocaleString('pt-BR')}</span>,
    },
  ];

  const selectedEntries = selectedGiveaway
    ? entries.filter((e) => e.giveawayId === selectedGiveaway.id)
    : [];

  return (
    <Layout title="Sorteios" subtitle="Gerenciar sorteios e participantes">
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Sorteios Ativos"
          value={activeCount}
          icon={<Gift size={18} />}
          color="accent"
          subtitle={`${giveaways.length} total`}
        />
        <StatsCard
          title="Participantes"
          value={totalEntries}
          icon={<Users size={18} />}
          color="primary"
        />
        <StatsCard
          title="Ganhadores"
          value={totalWinners}
          icon={<Trophy size={18} />}
          color="success"
        />
        <StatsCard
          title="Status"
          value={status ? 'Sistema OK' : 'Offline'}
          icon={<Clock size={18} />}
          color={status ? 'success' : 'error'}
        />
      </div>

      {/* Giveaway list */}
      <div className="section-header">
        <span className="section-title">Sorteios</span>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)}>
          <Plus size={14} />
          Novo Sorteio
        </button>
      </div>

      <DataTable
        columns={giveawayColumns}
        data={giveaways}
        filterable
        filterKeys={['name', 'status']}
        emptyMessage="Nenhum sorteio criado."
      />

      {/* Create giveaway modal */}
      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Novo Sorteio"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>Cancelar</button>
            <button className="btn btn-accent" onClick={() => {
              addToast('success', 'Sorteio criado (demo)');
              setShowCreateModal(false);
            }}>Criar Sorteio</button>
          </>
        }
      >
        <div>
          <label>Nome do Sorteio</label>
          <input className="input" placeholder="Ex: Sorteio de Inscritos" />
        </div>
        <div>
          <label>Regra de Participacao</label>
          <select className="select">
            <option value="follow">Apenas Follows</option>
            <option value="sub">Apenas Inscritos</option>
            <option value="all">Todos</option>
            <option value="chat">Comando no Chat</option>
          </select>
        </div>
        <div>
          <label>Numero de Ganhadores</label>
          <input className="input" type="number" value={1} min={1} />
        </div>
        <div>
          <label>Horario do Sorteio (opcional)</label>
          <input className="input" type="datetime-local" />
        </div>
      </Modal>

      {/* Selected giveaway detail */}
      <Modal
        open={!!selectedGiveaway}
        onClose={() => setSelectedGiveaway(null)}
        title={selectedGiveaway?.name || ''}
        footer={
          <button className="btn btn-secondary" onClick={() => setSelectedGiveaway(null)}>Fechar</button>
        }
      >
        {selectedGiveaway && (
          <div>
            <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
              <Badge variant={statusColors[selectedGiveaway.status]} dot>{statusLabels[selectedGiveaway.status]}</Badge>
              <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                {selectedGiveaway.entries} participantes
              </span>
            </div>

            {selectedEntries.length > 0 ? (
              <DataTable
                columns={entryColumns}
                data={selectedEntries}
                emptyMessage="Nenhum participante."
              />
            ) : (
              <div className="empty-state">Nenhum participante registrado.</div>
            )}
          </div>
        )}
      </Modal>
    </Layout>
  );
}
