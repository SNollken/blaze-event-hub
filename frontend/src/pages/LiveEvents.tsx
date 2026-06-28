import { useCallback, useState } from 'react';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { usePolling, addToast } from '../components/Toast';
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
} from 'lucide-react';

export default function LiveEvents() {
  const fetchEvents = useCallback(() => getEventsStatus(), []);
  const fetchStatus = useCallback(() => getStatus(), []);

  const { data: events, loading, reload: reloadEvents } = usePolling(fetchEvents, 5000);
  const { data: status } = usePolling(fetchStatus, 15000);

  const [logs, setLogs] = useState<{ time: string; message: string }[]>([]);
  const [starting, setStarting] = useState(false);
  const [stopping, setStopping] = useState(false);

  const addLog = (message: string) => {
    const time = new Date().toLocaleTimeString('pt-BR');
    setLogs((prev) => [...prev.slice(-99), { time, message }]);
  };

  const handleStart = async () => {
    setStarting(true);
    try {
      const res = await startEvents();
      addLog('Events iniciado com sucesso');
      addToast('success', 'Events Socket iniciado');
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Erro ao iniciar events';
      addLog(`ERRO: ${msg}`);
      addToast('error', msg);
    } finally {
      setStarting(false);
    }
  };

  const handleStop = async () => {
    setStopping(true);
    try {
      const res = await stopEvents();
      addLog('Events parado');
      addToast('success', 'Events Socket parado');
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Erro ao parar events';
      addLog(`ERRO: ${msg}`);
      addToast('error', msg);
    } finally {
      setStopping(false);
    }
  };

  const isRunning = events?.runnerRunning;

  return (
    <Layout
      title="Eventos ao Vivo"
      subtitle="Intake de eventos da Blaze"
      headerActions={
        isRunning ? (
          <button className="btn btn-danger btn-sm" onClick={handleStop} disabled={stopping}>
            <Square size={14} />
            {stopping ? 'Parando...' : 'Parar Events'}
          </button>
        ) : (
          <button className="btn btn-accent btn-sm" onClick={handleStart} disabled={starting}>
            <Play size={14} />
            {starting ? 'Iniciando...' : 'Iniciar Events'}
          </button>
        )
      }
    >
      {/* Stats */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Runner"
          value={events?.runnerRunning ? 'Rodando' : 'Parado'}
          icon={<Radio size={18} />}
          color={events?.runnerRunning ? 'success' : 'neutral'}
        />
        <StatsCard
          title="Cliente Socket"
          value={events?.clientRunning ? 'Conectado' : 'Desconectado'}
          icon={events?.clientRunning ? <Wifi size={18} /> : <WifiOff size={18} />}
          color={events?.clientRunning ? 'success' : 'error'}
        />
        <StatsCard
          title="Ultima Mensagem"
          value={events?.lastMessageType || '-'}
          icon={<MessageSquare size={18} />}
          color="accent"
        />
        <StatsCard
          title="Monitorado"
          value={status?.monitoredChannelConfigured ? 'Sim' : 'Nao'}
          icon={<Radio size={18} />}
          color={status?.monitoredChannelConfigured ? 'success' : 'warning'}
          subtitle={status?.monitoredChannelConfigured ? 'Canal configurado' : 'Configurar canal'}
        />
      </div>

      {/* Event status details */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 24 }}>
        <div className="section-header" style={{ marginBottom: 0 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600 }}>Status do Events Engine</h3>
          <button className="btn btn-secondary btn-sm" onClick={() => reloadEvents()}>
            <RefreshCw size={14} />
            Atualizar
          </button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16, marginTop: 16 }}>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Runner Status</div>
            <Badge variant={events?.runnerRunning ? 'success' : 'neutral'} dot>
              {events?.runnerRunning ? 'Rodando' : 'Parado'}
            </Badge>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Cliente Status</div>
            <Badge variant={events?.clientRunning ? 'success' : 'error'} dot>
              {events?.clientRunning ? 'Conectado' : 'Desconectado'}
            </Badge>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Session ID</div>
            <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {events?.sessionId || 'N/A'}
            </span>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Iniciado em</div>
            <span style={{ fontSize: 13 }}>
              {events?.startedAt
                ? new Date(events.startedAt).toLocaleString('pt-BR')
                : 'N/A'}
            </span>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Ultimo Tipo</div>
            <span className="mono" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {events?.lastMessageType || 'N/A'}
            </span>
          </div>
          <div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>Canal</div>
            <span style={{ fontSize: 13 }}>
              {status?.monitoredChannelConfigured ? 'Configurado' : 'Nao configurado'}
            </span>
          </div>
        </div>
      </div>

      {/* Log */}
      <div className="glass-card" style={{ padding: 20 }}>
        <div className="section-header" style={{ marginBottom: 12 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600 }}>Log de Eventos</h3>
          <Badge variant="neutral">{logs.length} entradas</Badge>
        </div>
        <div className="log-panel">
          {logs.length === 0 ? (
            <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 24 }}>
              Nenhum log ainda. Inicie o Events Engine para comecar a receber eventos.
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
