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
  ALWAYS: t('alerts.conditionAlways'),
  MIN_AMOUNT: t('alerts.conditionMinAmount'),
  RAID_MIN_SIZE: t('alerts.conditionRaidMinSize'),
};

const severityFor = (acknowledged: boolean): 'success' | 'warning' => acknowledged ? 'success' : 'warning';

// ponytail: async buttons disabled via actionLoading — no per-button loading spinner yet (1 action at a time)

export default function Alerts() {
  const fetchRules = useCallback(() => getAlertRules(), []);
  const fetchHistory = useCallback(() => getAlertHistory(), []);
  const fetchActive = useCallback(() => getActiveAlerts(), []);
  const fetchStats = useCallback(() => getAlertStats(), []);

  const { data: rules, loading: rulesLoading, error: rulesError, reload: reloadRules } = usePolling(fetchRules, 15000);
  const { data: history, loading: historyLoading, error: historyError, reload: reloadHistory } = usePolling(fetchHistory, 12000);
  const { data: activeAlerts, loading: activeLoading, error: activeError, reload: reloadActive } = usePolling(fetchActive, 10000);
  const { data: stats, loading: statsLoading, error: statsError, reload: reloadStats } = usePolling(fetchStats, 15000);

  const pollError = rulesError || historyError || activeError || statsError;
  const [actionLoading, setActionLoading] = useState<string | null>(null);

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
    setActionLoading('create-rule');
    try {
      await createAlertRule({
        ...form,
        name: form.name.trim(),
        template: form.template.trim() || null,
      });
      addToast('success', t('alerts.createSuccess'));
      setShowCreateModal(false);
      setForm({ ...form, name: '', template: '', threshold: 0 });
      await reloadAll();
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('alerts.createError'));
    } finally {
      setActionLoading(null);
    }
  };

  const simulateEvent = async () => {
    setActionLoading('simulate');
    try {
      await simulateBlazeEvent(form.eventType, `${t('alerts.simulateMessage')} ${form.eventType}`);
      addToast('success', t('alerts.simulateSuccess'));
      await reloadAll();
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('alerts.simulateError'));
    } finally {
      setActionLoading(null);
    }
  };

  const ruleColumns: Column<AlertRule>[] = [
    { key: 'name', header: t('common.name'), sortable: true },
    { key: 'eventType', header: t('alerts.colEvent'), render: (r) => <Badge variant="neutral">{r.eventType}</Badge> },
    { key: 'condition', header: t('alerts.colCondition'), render: (r) => conditionLabels[r.condition] },
    { key: 'threshold', header: t('common.threshold'), sortable: true },
    { key: 'enabled', header: t('common.status'), render: (r) => <Badge variant={r.enabled ? 'success' : 'neutral'} dot>{r.enabled ? t('alerts.statusActive') : t('alerts.statusInactive')}</Badge> },
    {
      key: 'actions', header: '', width: 80,
      render: (r) => (
        <button
          className="btn btn-danger btn-sm btn-icon"
          aria-label={`${t('alerts.removeRule')} ${r.name}`}
          disabled={actionLoading !== null}
          onClick={async () => {
            setActionLoading('delete-rule');
            try {
              await deleteAlertRule(r.id);
              addToast('success', t('alerts.deleteSuccess'));
              await reloadAll();
            } catch (error) {
              addToast('error', error instanceof Error ? error.message : t('alerts.deleteError'));
            } finally {
              setActionLoading(null);
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
      key: 'triggeredAt', header: t('alerts.colWhen'), width: 170,
      render: (a) => <span className="mono text-xs">{new Date(a.triggeredAt).toLocaleString(getLocale())}</span>,
    },
    { key: 'ruleName', header: t('alerts.colRule'), sortable: true },
    { key: 'eventType', header: t('alerts.colEvent'), render: (a) => <Badge variant="neutral">{a.eventType}</Badge> },
    { key: 'message', header: t('alerts.colMessage') },
    {
      key: 'acknowledged', header: t('common.status'),
      render: (a) => <Badge variant={severityFor(a.acknowledged)}>{a.acknowledged ? t('alerts.ackSuccess') : t('alerts.statusPending')}</Badge>,
    },
    {
      key: 'actions', header: '', width: 80,
      render: (a) => !a.acknowledged && (
        <button
          className="btn btn-secondary btn-sm btn-icon"
          aria-label={`${t('alerts.acknowledgeAlert')} ${a.ruleName}`}
          disabled={actionLoading !== null}
          onClick={async () => {
            setActionLoading('ack-alert');
            try {
              await acknowledgeAlert(a.id);
              addToast('success', t('alerts.ackSuccess'));
              await reloadAll();
            } catch (error) {
              addToast('error', error instanceof Error ? error.message : t('alerts.ackError'));
            } finally {
              setActionLoading(null);
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
    <Layout title={t('alerts.title')} subtitle={t('alerts.subtitle')}>
      {pollError && <ErrorBanner error={pollError} onRetry={reloadAll} />}
      <div className="stats-grid mb-6">
        <StatsCard title={t('alerts.activeRules')} value={statsLoading ? t('common.loading') : (stats?.enabledRules ?? 0)} icon={<Bell size={18} />} color="primary" subtitle={statsLoading ? '' : `${stats?.totalRules ?? 0} ${t('common.total')}`} />
        <StatsCard title={t('alerts.pending')} value={activeLoading ? t('common.loading') : (activeAlerts?.length ?? 0)} icon={<BellRing size={18} />} color="warning" subtitle={activeLoading ? '' : `${stats?.totalAlerts ?? 0} ${t('alerts.history')}`} />
        <StatsCard title={t('alerts.acknowledged')} value={statsLoading ? t('common.loading') : (stats?.acknowledgedAlerts ?? 0)} icon={<Check size={18} />} color="success" />
      </div>

      <div className="section-header">
        <div className="tabs mb-0">
          <button className={`tab ${activeTab === 'rules' ? 'active' : ''}`} onClick={() => setActiveTab('rules')}>{t('alerts.rulesTab')}</button>
          <button className={`tab ${activeTab === 'history' ? 'active' : ''}`} onClick={() => setActiveTab('history')}>{t('alerts.historyTab')}</button>
        </div>
        <div className="flex gap-sm">
          <button className="btn btn-secondary btn-sm" onClick={simulateEvent} disabled={actionLoading !== null}>
            <Play size={14} />
            {actionLoading === 'simulate' ? t('alerts.simulating') : t('alerts.simulate')}
          </button>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)} disabled={actionLoading !== null}>
            <Plus size={14} />
            {t('alerts.newRule')}
          </button>
        </div>
      </div>

      {activeTab === 'rules' ? (
        rulesLoading && !rules ? <div className="skeleton-list" /> : (
          <DataTable columns={ruleColumns} data={visibleRules} filterable filterKeys={['name', 'eventType', 'condition']} emptyMessage={t('alerts.emptyRules')} />
        )
      ) : (
        historyLoading && !history ? <div className="skeleton-list" /> : (
          <DataTable columns={alertColumns} data={visibleHistory} filterable filterKeys={['ruleName', 'message', 'eventType']} emptyMessage={t('alerts.emptyHistory')} />
        )
      )}

      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title={t('alerts.newRule')}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)} disabled={actionLoading !== null}>{t('common.cancel')}</button>
            <button className="btn btn-primary" onClick={createRule} disabled={!form.name.trim() || actionLoading !== null}>
              {actionLoading === 'create-rule' ? t('alerts.creating') : t('alerts.createRule')}
            </button>
          </>
        }
      >
        <div>
          <label htmlFor="alert-name">{t('alerts.ruleNameLabel')}</label>
          <input id="alert-name" className="input" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder={t('alerts.ruleNamePlaceholder')} />
        </div>
        <div>
          <label htmlFor="alert-event-type">{t('alerts.eventTypeLabel')}</label>
          <select id="alert-event-type" className="select" value={form.eventType} onChange={(event) => setForm({ ...form, eventType: event.target.value as BlazeEventType })}>
            {eventTypes.map((type) => <option key={type} value={type}>{type}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="alert-condition">{t('alerts.colCondition')}</label>
          <select id="alert-condition" className="select" value={form.condition} onChange={(event) => setForm({ ...form, condition: event.target.value as AlertCondition })}>
            {conditions.map((condition) => <option key={condition} value={condition}>{conditionLabels[condition]}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="alert-threshold">{t('common.threshold')}</label>
          <input id="alert-threshold" className="input" type="number" min={0} value={form.threshold} onChange={(event) => setForm({ ...form, threshold: Number(event.target.value) })} />
        </div>
        <div>
          <label htmlFor="alert-template">{t('alerts.templateLabel')}</label>
          <input id="alert-template" className="input" value={form.template} onChange={(event) => setForm({ ...form, template: event.target.value })} placeholder={t('common.optional')} />
        </div>
      </Modal>
    </Layout>
  );
}
