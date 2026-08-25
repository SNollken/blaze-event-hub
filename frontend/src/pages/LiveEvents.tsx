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

import {
  Play,
  Square,
  MessageSquare,
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
      addLog(`${t('events.errorPrefix')} ${msg}`);
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
      addLog(`${t('events.errorPrefix')} ${msg}`);
      addToast('error', msg);
    } finally {
      setStopping(false);
    }
  };

  const isRunning = events?.runnerRunning;

  return (
    <Layout
      title={t('events.title')}
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

      {/* Status */}
      <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 mb-5 text-[12px] text-text-secondary">
        <span className="inline-flex items-center gap-1.5">
          <span className={`status-dot ${!eventsLoading && isRunning ? 'active' : 'inactive'}`} aria-hidden="true" />
          {t('events.runner')}: {eventsLoading ? t('common.loading') : isRunning ? t('common.running') : t('common.stopped')}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className={`status-dot ${!eventsLoading && events?.clientRunning ? 'active' : 'inactive'}`} aria-hidden="true" />
          {t('events.socketClient')}: {eventsLoading ? t('common.loading') : events?.clientRunning ? t('common.connected') : t('common.disconnected')}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className={`status-dot ${statusLoading ? 'inactive' : status?.monitoredChannelConfigured ? 'active' : 'warning'}`} aria-hidden="true" />
          {t('events.channel')}: {statusLoading ? t('common.loading') : status?.monitoredChannelConfigured ? t('common.configured') : t('common.notConfigured')}
        </span>
      </div>

      {/* Counters */}
      <div className="stats-grid mb-6">
        <StatsCard
          title={t('events.messagesSeen')}
          value={eventsLoading ? t('common.loading') : (events?.messagesSeen ?? 0)}
          icon={<MessageSquare size={18} />}
          color="accent"
        />
        <StatsCard
          title={t('events.acceptedEvents')}
          value={eventsLoading ? t('common.loading') : (events?.acceptedEvents ?? 0)}
          icon={<CheckCircle size={18} />}
          color="success"
        />
        <StatsCard
          title={t('events.rejectedEvents')}
          value={eventsLoading ? t('common.loading') : (events?.rejectedEvents ?? 0)}
          icon={<XCircle size={18} />}
          color={eventsLoading ? 'neutral' : events?.rejectedEvents && events.rejectedEvents > 0 ? 'warning' : 'neutral'}
        />
      </div>

      {/* Log */}
      <div className="glass-card p-5">
        <div className="section-header mb-3">
          <h2 className="text-sm font-semibold">{t('events.log')}</h2>
          <Badge variant="neutral">{logs.length} {t('events.entries')}</Badge>
        </div>
        <div className="log-panel">
          {logs.length === 0 ? (
            <div className="text-text-muted text-center p-6">
              {t('events.emptyLog')}
            </div>
          ) : (
            logs.map((log, i) => (
              <div key={i} className="log-line">
                <span className="timestamp">{log.time}</span>
                <span className={log.message.startsWith(t('events.errorPrefix')) ? 'text-error' : 'text-text-secondary'}>
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
