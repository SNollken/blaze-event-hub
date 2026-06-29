import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { DataTable, Column } from '../components/DataTable';
import { usePolling, addToast } from '../components/Toast';
import {
  acknowledgeAlert,
  createAlertRule,
  deleteAlertRule,
  getActiveAlerts,
  getAlertHistory,
  getAlertRules,
  getAlertStats,
  simulateBlazeEvent,
} from '../api/client';
import { AlertCondition, AlertEvent, AlertRule, BlazeEventType } from '../api/types';
import { Bell, BellRing, Check, Play, Plus, Trash2 } from 'lucide-react';

const eventTypes: BlazeEventType[] = [
  'channel.follow',
  'channel.subscribe',
  'channel.subscription.gift',
  'channel.vote',
  'channel.chat.message',
];

const conditions: AlertCondition[] = ['ALWAYS', 'MIN_AMOUNT', 'RAID_MIN_SIZE'];

const conditionLabels: Record<AlertCondition, string> = {
  ALWAYS: 'Sempre',
  MIN_AMOUNT: 'Valor minimo',
  RAID_MIN_SIZE: 'Raid minimo',
};

const severityFor = (acknowledged: boolean): 'success' | 'warning' => acknowledged ? 'success' : 'warning';

export default function Alerts() {
  const fetchRules = useCallback(() => getAlertRules(), []);
  const fetchHistory = useCallback(() => getAlertHistory(), []);
  const fetchActive = useCallback(() => getActiveAlerts(), []);
  const fetchStats = useCallback(() => getAlertStats(), []);

  const { data: rules, loading: rulesLoading, reload: reloadRules } = usePolling(fetchRules, 15000);
  const { data: history, loading: historyLoading, reload: reloadHistory } = usePolling(fetchHistory, 12000);
  const { data: activeAlerts, reload: reloadActive } = usePolling(fetchActive, 10000);
  const { data: stats, reload: reloadStats } = usePolling(fetchStats, 15000);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [activeTab, setActiveTab] = useState<'rules' | 'history'>('rules');
  const [form, setForm] = useState({
    name: '',
    eventType: 'channel.follow' as BlazeEventType,
    condition: 'ALWAYS' as AlertCondition,
    threshold: 0,
    template: '',
    enabled: true,
    cooldownMs: 0,
  });

  const reloadAll = async () => {
    await Promise.all([reloadRules(), reloadHistory(), reloadActive(), reloadStats()]);
  };

  const createRule = async () => {
    try {
      await createAlertRule({
        ...form,
        name: form.name.trim(),
        template: form.template.trim() || null,
      });
      addToast('success', 'Regra criada');
      setShowCreateModal(false);
      setForm({ ...form, name: '', template: '', threshold: 0 });
      await reloadAll();
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : 'Erro ao criar regra');
    }
  };

  const simulateEvent = async () => {
    try {
      await simulateBlazeEvent(form.eventType, `Evento simulado para ${form.eventType}`);
      addToast('success', 'Evento simulado enviado');
      await reloadAll();
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : 'Erro ao simular evento');
    }
  };

  const ruleColumns: Column<AlertRule>[] = [
    { key: 'name', header: 'Nome', sortable: true },
    { key: 'eventType', header: 'Evento', render: (r) => <Badge variant="neutral">{r.eventType}</Badge> },
    { key: 'condition', header: 'Condicao', render: (r) => conditionLabels[r.condition] },
    { key: 'threshold', header: 'Limite', sortable: true },
    { key: 'enabled', header: 'Status', render: (r) => <Badge variant={r.enabled ? 'success' : 'neutral'} dot>{r.enabled ? 'Ativa' : 'Inativa'}</Badge> },
    {
      key: 'actions', header: '', width: 80,
      render: (r) => (
        <button
          className="btn btn-danger btn-sm btn-icon"
          aria-label={`Remover regra ${r.name}`}
          onClick={async () => {
            try {
              await deleteAlertRule(r.id);
              addToast('success', 'Regra removida');
              await reloadAll();
            } catch (error) {
              addToast('error', error instanceof Error ? error.message : 'Erro ao remover regra');
            }
          }}
        >
          <Trash2 size={12} />
        </button>
      ),
    },
  ];

  const alertColumns: Column<AlertEvent>[] = [
    {
      key: 'triggeredAt', header: 'Quando', width: 170,
      render: (a) => <span className="mono" style={{ fontSize: 12 }}>{new Date(a.triggeredAt).toLocaleString('pt-BR')}</span>,
    },
    { key: 'ruleName', header: 'Regra', sortable: true },
    { key: 'eventType', header: 'Evento', render: (a) => <Badge variant="neutral">{a.eventType}</Badge> },
    { key: 'message', header: 'Mensagem' },
    {
      key: 'acknowledged', header: 'Status',
      render: (a) => <Badge variant={severityFor(a.acknowledged)}>{a.acknowledged ? 'Reconhecido' : 'Pendente'}</Badge>,
    },
    {
      key: 'actions', header: '', width: 80,
      render: (a) => !a.acknowledged && (
        <button
          className="btn btn-secondary btn-sm btn-icon"
          aria-label={`Reconhecer alerta ${a.ruleName}`}
          onClick={async () => {
            try {
              await acknowledgeAlert(a.id);
              addToast('success', 'Alerta reconhecido');
              await reloadAll();
            } catch (error) {
              addToast('error', error instanceof Error ? error.message : 'Erro ao reconhecer alerta');
            }
          }}
        >
          <Check size={12} />
        </button>
      ),
    },
  ];

  const visibleHistory = history || [];
  const visibleRules = rules || [];

  return (
    <Layout title="Alertas" subtitle="Regras, disparos e historico conectados ao backend">
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard title="Regras Ativas" value={stats?.enabledRules ?? 0} icon={<Bell size={18} />} color="primary" subtitle={`${stats?.totalRules ?? 0} total`} />
        <StatsCard title="Alertas Pendentes" value={activeAlerts?.length ?? 0} icon={<BellRing size={18} />} color="warning" subtitle={`${stats?.totalAlerts ?? 0} historico`} />
        <StatsCard title="Reconhecidos" value={stats?.acknowledgedAlerts ?? 0} icon={<Check size={18} />} color="success" />
      </div>

      <div className="section-header">
        <div className="tabs" style={{ marginBottom: 0 }}>
          <button className={`tab ${activeTab === 'rules' ? 'active' : ''}`} onClick={() => setActiveTab('rules')}>Regras</button>
          <button className={`tab ${activeTab === 'history' ? 'active' : ''}`} onClick={() => setActiveTab('history')}>Historico</button>
        </div>
        <div className="flex gap-sm">
          <button className="btn btn-secondary btn-sm" onClick={simulateEvent}>
            <Play size={14} />
            Simular Evento
          </button>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)}>
            <Plus size={14} />
            Nova Regra
          </button>
        </div>
      </div>

      {activeTab === 'rules' ? (
        rulesLoading && !rules ? <div className="skeleton-list" /> : (
          <DataTable columns={ruleColumns} data={visibleRules} filterable filterKeys={['name', 'eventType', 'condition']} emptyMessage="Nenhuma regra configurada." />
        )
      ) : (
        historyLoading && !history ? <div className="skeleton-list" /> : (
          <DataTable columns={alertColumns} data={visibleHistory} filterable filterKeys={['ruleName', 'message', 'eventType']} emptyMessage="Nenhum alerta registrado." />
        )
      )}

      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Nova Regra de Alerta"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>Cancelar</button>
            <button className="btn btn-primary" onClick={createRule} disabled={!form.name.trim()}>Criar Regra</button>
          </>
        }
      >
        <div>
          <label htmlFor="alert-name">Nome da Regra</label>
          <input id="alert-name" className="input" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Ex: Alerta de follow" />
        </div>
        <div>
          <label htmlFor="alert-event-type">Tipo de Evento</label>
          <select id="alert-event-type" className="select" value={form.eventType} onChange={(event) => setForm({ ...form, eventType: event.target.value as BlazeEventType })}>
            {eventTypes.map((type) => <option key={type} value={type}>{type}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="alert-condition">Condicao</label>
          <select id="alert-condition" className="select" value={form.condition} onChange={(event) => setForm({ ...form, condition: event.target.value as AlertCondition })}>
            {conditions.map((condition) => <option key={condition} value={condition}>{conditionLabels[condition]}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="alert-threshold">Limite</label>
          <input id="alert-threshold" className="input" type="number" min={0} value={form.threshold} onChange={(event) => setForm({ ...form, threshold: Number(event.target.value) })} />
        </div>
        <div>
          <label htmlFor="alert-template">Template da mensagem</label>
          <input id="alert-template" className="input" value={form.template} onChange={(event) => setForm({ ...form, template: event.target.value })} placeholder="Opcional" />
        </div>
      </Modal>
    </Layout>
  );
}
