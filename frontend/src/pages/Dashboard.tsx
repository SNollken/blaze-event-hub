import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { StatsCard } from '../components/StatsCard';
import { StatusDot } from '../components/Badge';
import { usePolling } from '../components/Toast';
import { getStatus, getEvents, getMe } from '../api/client';
import {
  Server,
  Trophy,
  Calendar,
  Clock,
  Zap,
  ArrowRight,
} from 'lucide-react';
import type { EventResponse } from '../api/types';

function formatUptime(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}min`;
  return `${m}min`;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const fetchStatus = useCallback(() => getStatus(), []);
  const fetchEvents = useCallback(() => getEvents(), []);
  const fetchMe = useCallback(() => getMe().catch(() => null), []);

  const { data: status, loading: statusLoading } = usePolling(fetchStatus, 10000);
  const { data: events } = usePolling(fetchEvents, 8000);
  const { data: me } = usePolling(fetchMe, 30000);

  if (statusLoading && !status) {
    return (
      <Layout title="Visao Geral">
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div>Carregando...</div>
        </div>
      </Layout>
    );
  }

  const openEvents = (events ?? []).filter((e: EventResponse) => e.status === 'OPEN');
  const myEvents = (events ?? []).filter((e: EventResponse) => me && e.creatorMemberId === me.id);

  return (
    <Layout title="Visao Geral" subtitle="Blaze Event Hub">
      {/* Stats row */}
      <div className="stats-grid" style={{ marginBottom: 24 }}>
        <StatsCard
          title="Backend"
          value={status ? 'Online' : 'Offline'}
          icon={<Server size={18} />}
          color={status ? 'success' : 'error'}
          subtitle={status ? `${status.appName} ${status.version}` : 'Indisponivel'}
        />
        <StatsCard
          title="Eventos Abertos"
          value={openEvents.length}
          icon={<Calendar size={18} />}
          color="accent"
          subtitle="aguardando sorteio"
        />
        <StatsCard
          title="Meus Eventos"
          value={myEvents.length}
          icon={<Trophy size={18} />}
          color="primary"
          subtitle="que eu criei"
        />
        <StatsCard
          title="Java"
          value={status?.javaVersion ?? '-'}
          icon={<Zap size={18} />}
          color="primary"
        />
        <StatsCard
          title="Uptime"
          value={status ? formatUptime(status.uptimeSeconds) : '-'}
          icon={<Clock size={18} />}
          color="primary"
        />
      </div>

      {/* Quick actions + Status */}
      <div className="responsive-grid-2" style={{ marginBottom: 24 }}>
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Acoes Rapidas</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button
              className="btn btn-primary"
              onClick={() => navigate('/events/create')}
              style={{ justifyContent: 'space-between', width: '100%' }}
            >
              Criar Novo Evento
              <ArrowRight size={14} />
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/events')}
              style={{ justifyContent: 'space-between', width: '100%' }}
            >
              Ver Todos os Eventos
              <ArrowRight size={14} />
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => navigate('/my-events')}
              style={{ justifyContent: 'space-between', width: '100%' }}
            >
              Meus Eventos
              <ArrowRight size={14} />
            </button>
          </div>
        </div>

        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Status do Sistema</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Backend conectado</span>
              <StatusDot status={status ? 'active' : 'error'} label={status ? 'Sim' : 'Nao'} />
            </div>
            {me ? (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Usuario logado</span>
                <StatusDot status="active" label={me.displayName || me.blazeUsername} />
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Usuario logado</span>
                <StatusDot status="inactive" label="Nao identificado" />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Recent open events */}
      {openEvents.length > 0 && (
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Eventos Abertos</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {openEvents.slice(0, 5).map((event) => (
              <div
                key={event.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '10px 14px',
                  borderRadius: 'var(--radius)',
                  border: '1px solid var(--border)',
                  cursor: 'pointer',
                }}
                onClick={() => navigate(`/events/${event.id}`)}
              >
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{event.title}</div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {event.rulesMode || 'Modo padrao'}
                  </div>
                </div>
                <ArrowRight size={14} style={{ color: 'var(--text-muted)' }} />
              </div>
            ))}
          </div>
        </div>
      )}
    </Layout>
  );
}
