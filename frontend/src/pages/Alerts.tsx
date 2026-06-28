import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { usePolling, addToast } from '../components/Toast';
import { getStatus } from '../api/client';
import {
  Bell,
  BellRing,
  Plus,
  Trash2,
  Clock,
  Filter,
} from 'lucide-react';

interface AlertRule {
  id: string;
  name: string;
  type: string;
  condition: string;
  active: boolean;
  createdAt: string;
}

interface AlertEvent {
  id: string;
  ruleName: string;
  message: string;
  severity: 'info' | 'warning' | 'critical';
  acknowledged: boolean;
  createdAt: string;
}

// Demo data for UI purposes - will be replaced with API calls
const demoRules: AlertRule[] = [
  { id: '1', name: 'Evento de follow', type: 'follow', condition: 'Quando recebido', active: true, createdAt: '2024-01-15' },
  { id: '2', name: 'Mensagem de chat', type: 'chat', condition: 'Contem palavra-chave', active: true, createdAt: '2024-01-15' },
  { id: '3', name: 'Subscribers novos', type: 'subscribe', condition: 'Novo inscrito', active: false, createdAt: '2024-01-10' },
];

const demoAlerts: AlertEvent[] = [
  { id: '1', ruleName: 'Evento de follow', message: 'Novo follow recebido', severity: 'info', acknowledged: false, createdAt: '2024-01-15T10:30:00' },
  { id: '2', ruleName: 'Mensagem de chat', message: 'Mensagem com palavra-chave detectada', severity: 'warning', acknowledged: true, createdAt: '2024-01-15T10:25:00' },
  { id: '3', ruleName: 'Subscribers novos', message: 'Novo subscriber detectado', severity: 'info', acknowledged: false, createdAt: '2024-01-15T10:20:00' },
];

const severityColors: Record<string, 'success' | 'warning' | 'error'> = {
  info: 'success',
  warning: 'warning',
  critical: 'error',
};

export default function Alerts() {
  const fetchStatus = useCallback(() => getStatus(), []);
  const { data: status } = usePolling(fetchStatus, 15000);

  const [rules, setRules] = useState<AlertRule[]>(demoRules);
  const [alerts] = useState<AlertEvent[]>(demoAlerts);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [activeTab, setActiveTab] = useState<'rules' | 'history'>('rules');

  const ruleColumns: Column<AlertRule>[] = [
    { key: 'name', header: 'Nome', sortable: true },
    { key: 'type', header: 'Tipo', render: (r) => <Badge variant="neutral">{r.type}</Badge> },
    { key: 'condition', header: 'Condicao' },
    { key: 'active', header: 'Status', render: (r) => <Badge variant={r.active ? 'success' : 'neutral'} dot>{r.active ? 'Ativa' : 'Inativa'}</Badge> },
    {
      key: 'actions', header: '', width: 80,
      render: (r) => (
        <button className="btn btn-danger btn-sm" onClick={() => {
          setRules(rules.filter((x) => x.id !== r.id));
          addToast('success', 'Regra removida');
        }}>
          <Trash2 size={12} />
        </button>
      ),
    },
  ];

  const alertColumns: Column<AlertEvent>[] = [
    {
      key: 'createdAt', header: 'Quando', width: 160,
      render: (a) => <span className="mono" style={{ fontSize: 12 }}>{new Date(a.createdAt).toLocaleString('pt-BR')}</span>,
    },
    { key: 'ruleName', header: 'Regra', sortable: true },
    { key: 'message', header: 'Mensagem' },
    {
      key: 'severity', header: 'Severidade',
      render: (a) => <Badge variant={severityColors[a.severity] || 'neutral'}>{a.severity}</Badge>,
    },
    {
      key: 'acknowledged', header: 'Status',
      render: (a) => <Badge variant={a.acknowledged ? 'success' : 'warning'}>{a.acknowledged ? 'Reconhecido' : 'Pendente'}</Badge>,
    },
  ];

  return (
    <Layout title="Alertas" subtitle="Regras e historico de alertas">
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Regras Ativas"
          value={rules.filter((r) => r.active).length}
          icon={<Bell size={18} />}
          color="primary"
          subtitle={`${rules.length} total`}
        />
        <StatsCard
          title="Alertas Pendentes"
          value={alerts.filter((a) => !a.acknowledged).length}
          icon={<BellRing size={18} />}
          color="warning"
          subtitle={`${alerts.length} total`}
        />
        <StatsCard
          title="Sistema"
          value={status ? 'Online' : 'Offline'}
          icon={<Clock size={18} />}
          color={status ? 'success' : 'error'}
        />
      </div>

      {/* Tabs */}
      <div className="tabs">
        <button
          className={`tab ${activeTab === 'rules' ? 'active' : ''}`}
          onClick={() => setActiveTab('rules')}
        >
          Regras
        </button>
        <button
          className={`tab ${activeTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveTab('history')}
        >
          Historico
        </button>
      </div>

      {/* Rules tab */}
      {activeTab === 'rules' && (
        <div>
          <div className="section-header">
            <span className="section-title">Regras de Alerta</span>
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)}>
              <Plus size={14} />
              Nova Regra
            </button>
          </div>
          <DataTable
            columns={ruleColumns}
            data={rules}
            filterable
            filterKeys={['name', 'type', 'condition']}
            emptyMessage="Nenhuma regra configurada."
          />
        </div>
      )}

      {/* History tab */}
      {activeTab === 'history' && (
        <div>
          <div className="section-header">
            <span className="section-title">Historico de Alertas</span>
          </div>
          <DataTable
            columns={alertColumns}
            data={alerts}
            filterable
            filterKeys={['ruleName', 'message']}
            emptyMessage="Nenhum alerta registrado."
          />
        </div>
      )}

      {/* Create rule modal */}
      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Nova Regra de Alerta"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>Cancelar</button>
            <button className="btn btn-primary" onClick={() => {
              addToast('success', 'Regra criada (demo)');
              setShowCreateModal(false);
            }}>Criar Regra</button>
          </>
        }
      >
        <div>
          <label>Nome da Regra</label>
          <input className="input" placeholder="Ex: Alerta de follow" />
        </div>
        <div>
          <label>Tipo de Evento</label>
          <select className="select">
            <option value="follow">Follow</option>
            <option value="chat">Mensagem de Chat</option>
            <option value="subscribe">Subscriber</option>
            <option value="raid">Raid</option>
          </select>
        </div>
        <div>
          <label>Condicao</label>
          <input className="input" placeholder="Ex: Quando recebido" />
        </div>
        <div>
          <label>Canal de Notificacao</label>
          <select className="select">
            <option value="overlay">Overlay</option>
            <option value="console">Console</option>
          </select>
        </div>
      </Modal>
    </Layout>
  );
}
