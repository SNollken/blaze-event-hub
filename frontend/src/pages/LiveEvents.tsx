import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { ErrorBanner } from '../components/ErrorBanner';
import { usePolling } from '../hooks/usePolling';
import { addToast } from '../components/Toast';
import { t, getLocale } from '../i18n';
import {
  getStatus,
  getEventsStatus,
  startEvents,
  stopEvents,
} from '../api/client';
import type { BlazeEventsStatusResponse } from '../api/types';
import {
  Radio,
  Play,
  Square,
  RefreshCw,
  Wifi,
  WifiOff,
  MessageSquare,
  Clock,
  CheckCircle,
  XCircle,
} from 'lucide-react';

export default function LiveEvents() {
  const fetchEvents = useCallback(() => getEventsStatus(), []);
  const fetchStatus = useCallback(() => getStatus(), []);

  const { data: events, loading: eventsLoading, error: eventsError, reload: reloadEvents } = usePolling(fetchEvents, 5000);
  const { data: status, loading: statusLoading, error: statusError, reload: reloadStatus } = usePolling(fetchStatus, 15000);

  const pollError = eventsError || statusError;

  const [logs, setLogs] = useState<{ time: string; message: string }[]>([]);
  const [starting, setStarting] = useState(false);
  const [stopping, setStopping] = useState(false);

  const addLog = (message: string) => {
    const time = new Date().toLocaleTimeString(getLocale());
    setLogs((prev) => [...prev.slice(-99), { time, message }]);
  };

  const handleStart = async () => {
    setStarting(true);
    try {
      await startEvents();
      await reloadEvents();
      await reloadStatus();
      addLog(t('events.startSuccess'));
      addToast('success', t('events.startSuccess'));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : t('events.startError');
      addLog(`ERRO: ${msg}`);
      addToast('error', msg);
    } finally {
      setStarting(false);
    }
  };

  const handleStop = async () => {
    setStopping(true);
    try {
      await stopEvents();
      await reloadEvents();
      await reloadStatus();
      addLog(t('events.stopSuccess'));
      addToast('success', t('events.stopSuccess'));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : t('events.stopError');
      addLog(`ERRO: ${msg}`);
      addToast('error', msg);
    } finally {
      setStopping(false);
    }
  };

  const isRunning = events?.runnerRunning;

  return (
    <Layout
      title={t('events.title')}
      subtitle={t('events.subtitle')}
      headerActions={
        isRunning ? (
          <button className="btn btn-danger btn-sm" onClick={handleStop} disabled={stopping}>
            <Square size={14} />
            {stopping ? t('events.stopping') : t('events.stop')}
          </button>
        ) : (
          <button className="btn btn-accent btn-sm" onClick={handleStart} disabled={starting}>
            <Play size={14} />
            {starting ? t('events.starting') : t('events.start')}
          </button>
        )
      }
    >
      {pollError && <ErrorBanner error={pollError} onRetry={() => { void reloadEvents(); void reloadStatus(); }} />}
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title={t('events.runner')}
          value={eventsLoading ? t('common.loading') : events?.runnerRunning ? t('common.running') : t('common.stopped')}
          icon={<Radio size={18} />}
          color={eventsLoading ? 'neutral' : events?.runnerRunning ? 'success' : 'neutral'}
        />
        <StatsCard
          title={t('events.socketClient')}
          value={eventsLoading ? t('common.loading') : events?.clientRunning ? t('common.connected') : t('common.disconnected')}
          icon={events?.clientRunning ? <Wifi size={18} /> : <WifiOff size={18} />}
          color={eventsLoading ? 'neutral' : events?.clientRunning ? 'success' : 'error'}
        />
        <StatsCard
          title={t('events.lastMessage')}
          value={eventsLoading ? t('common.loading') : (events?.lastMessageType || '-')}
          icon={<MessageSquare size={18} />}
          color="accent"
        />
        <StatsCard
          title={t('events.monitored')}
          value={statusLoading ? t('common.loading') : status?.monitoredChannelConfigured ? t('common.yes') : t('common.no')}
          icon={<Radio size={18} />}
          color={statusLoading ? 'neutral' : status?.monitoredChannelConfigured ? 'success' : 'warning'}
          subtitle={statusLoading ? '' : status?.monitoredChannelConfigured ? t('common.configured') : t('common.notConfigured')}
        />
        <StatsCard
          title={t('events.messagesSeen')}
          value={eventsLoading ? t('common.loading') : (events?.messagesSeen ?? 0)}
          icon={<MessageSquare size={18} />}
          color="accent"
          subtitle={eventsLoading ? '' : events?.runnerRunning ? t('events.sinceStart') : t('events.stopped')}
        />
        <StatsCard
          title={t('events.acceptedEvents')}
          value={eventsLoading ? t('common.loading') : (events?.acceptedEvents ?? 0)}
          icon={<CheckCircle size={18} />}
          color="success"
          subtitle={eventsLoading ? '' : events?.runnerRunning ? t('events.processed') : t('events.stopped')}
        />
        <StatsCard
          title={t('events.rejectedEvents')}
          value={eventsLoading ? t('common.loading') : (events?.rejectedEvents ?? 0)}
          icon={<XCircle size={18} />}
          color={eventsLoading ? 'neutral' : events?.rejectedEvents && events.rejectedEvents > 0 ? 'warning' : 'neutral'}
          subtitle={eventsLoading ? '' : events?.runnerRunning ? t('events.ignored') : t('events.stopped')}
        />
      </div>

      {/* Event status details */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 24 }}>
        <div className="section-header" style={{ marginBottom: 0 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600 }}>{t('events.engineStatus')}</h3>
          <button className="btn btn-secondary btn-sm" onClick={() => { void reloadEvents(); void reloadStatus(); }}>
            <RefreshCw size={14} />
            {t('common.refresh')}
          </button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16, marginTop: 16 }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>{t('events.runnerStatus')}</div>
            <Badge variant={events?.runnerRunning ? 'success' : 'neutral'} dot>
              {events?.runnerRunning ? t('common.running') : t('common.stopped')}
            </Badge>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>{t('events.clientStatus')}</div>
            <Badge variant={events?.clientRunning ? 'success' : 'error'} dot>
              {events?.clientRunning ? t('common.connected') : t('common.disconnected')}
            </Badge>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>{t('events.sessionId')}</div>
            <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {events?.sessionId || t('common.na')}
            </span>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>{t('events.startedAt')}</div>
            <span style={{ fontSize: 13 }}>
              {events?.startedAt
                ? new Date(events.startedAt).toLocaleString(getLocale())
                : t('common.na')}
            </span>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>{t('events.lastType')}</div>
            <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {events?.lastMessageType || t('common.na')}
            </span>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>{t('events.channel')}</div>
            <span style={{ fontSize: 13 }}>
              {status?.monitoredChannelConfigured ? t('common.configured') : t('common.notConfigured')}
            </span>
          </div>
        </div>
      </div>

      {/* Log */}
      <div className="glass-card" style={{ padding: 20 }}>
        <div className="section-header" style={{ marginBottom: 12 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600 }}>{t('events.log')}</h3>
          <Badge variant="neutral">{logs.length} {t('events.entries')}</Badge>
        </div>
        <div className="log-panel">
          {logs.length === 0 ? (
            <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 24 }}>
              {t('events.emptyLog')}
            </div>
          ) : (
            logs.map((log, i) => (
              <div key={i} className="log-line">
                <span className="timestamp">{log.time}</span>
                <span style={{ color: log.message.startsWith('ERRO') ? 'var(--error)' : 'var(--text-secondary)' }}>
                  {log.message}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </Layout>
  );
}
