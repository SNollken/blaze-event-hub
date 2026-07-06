import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { Badge } from '../components/Badge';
import { usePolling } from '../components/Toast';
import { getEvents } from '../api/client';
import { ArrowRight, Calendar, Users } from 'lucide-react';
import type { EventResponse, EventStatus } from '../api/types';

const statusBadge: Record<string, { variant: 'success' | 'warning' | 'error' | 'neutral'; label: string }> = {
  DRAFT: { variant: 'neutral', label: 'Rascunho' },
  OPEN: { variant: 'success', label: 'Aberto' },
  CLOSED: { variant: 'warning', label: 'Fechado' },
  DRAWING: { variant: 'warning', label: 'Sorteando' },
  COMPLETED: { variant: 'success', label: 'Concluido' },
  CANCELLED: { variant: 'error', label: 'Cancelado' },
};

function formatDate(s: string) {
  return new Date(s).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: '2-digit' });
}

export default function Events() {
  const navigate = useNavigate();
  const fetchEvents = useCallback(() => getEvents('OPEN'), []);

  const { data: events, loading, error } = usePolling(fetchEvents, 8000);

  return (
    <Layout
      title="Eventos"
      subtitle="Eventos abertos para participacao"
      headerActions={
        <button className="btn btn-primary btn-sm" onClick={() => navigate('/events/create')}>
          Criar Evento
        </button>
      }
    >
      {loading && !events ? (
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div>Carregando eventos...</div>
        </div>
      ) : error ? (
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div style={{ color: 'var(--error)' }}>Erro ao carregar eventos</div>
          <div style={{ fontSize: 12 }}>{error}</div>
        </div>
      ) : !events || events.length === 0 ? (
        <div className="empty-state" style={{ minHeight: 300 }}>
          <Calendar size={48} />
          <div style={{ fontSize: 15, fontWeight: 600 }}>Nenhum evento aberto</div>
          <div style={{ fontSize: 13 }}>
            <button className="btn btn-primary btn-sm" onClick={() => navigate('/events/create')}>
              Criar Primeiro Evento
            </button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {events.map((event) => {
            const st = statusBadge[event.status] ?? statusBadge.DRAFT;
            return (
              <div
                key={event.id}
                className="glass-card"
                style={{
                  padding: '16px 20px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  cursor: 'pointer',
                }}
                onClick={() => navigate(`/events/${event.id}`)}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 16, flex: 1, minWidth: 0 }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>
                        {event.title}
                      </span>
                      <Badge variant={st.variant} dot>
                        {st.label}
                      </Badge>
                    </div>
                    {event.description && (
                      <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {event.description}
                      </div>
                    )}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 12, color: 'var(--text-muted)' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <Users size={12} />
                        {event.rulesMode || 'Regras padrao'}
                      </span>
                      {event.startsAt && <span>Inicio: {formatDate(event.startsAt)}</span>}
                    </div>
                  </div>
                </div>
                <ArrowRight size={16} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
              </div>
            );
          })}
        </div>
      )}
    </Layout>
  );
}
