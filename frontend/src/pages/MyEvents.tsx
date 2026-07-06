import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { Badge } from '../components/Badge';
import { usePolling } from '../components/Toast';
import { getEvents, getMe } from '../api/client';
import { ArrowRight, UserCircle } from 'lucide-react';
import type { EventResponse } from '../api/types';

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

export default function MyEvents() {
  const navigate = useNavigate();
  const fetchEvents = useCallback(() => getEvents(), []);
  const fetchMe = useCallback(() => getMe(), []);

  const { data: events, loading, error } = usePolling(fetchEvents, 8000);
  const { data: me, loading: meLoading } = usePolling(fetchMe, 30000);

  const myEvents = (events ?? []).filter((e: EventResponse) => me && e.creatorMemberId === me.id);

  if (loading || meLoading) {
    return (
      <Layout title="Meus Eventos">
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div>Carregando...</div>
        </div>
      </Layout>
    );
  }

  if (error) {
    return (
      <Layout title="Meus Eventos">
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div style={{ color: 'var(--error)' }}>Erro ao carregar eventos</div>
          <div style={{ fontSize: 12 }}>{error}</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout title="Meus Eventos" subtitle="Eventos que voce criou">
      {!me ? (
        <div className="empty-state" style={{ minHeight: 300 }}>
          <UserCircle size={48} />
          <div style={{ fontSize: 15, fontWeight: 600 }}>Nao identificado</div>
          <div style={{ fontSize: 13 }}>Nao foi possivel identificar o usuario logado</div>
        </div>
      ) : myEvents.length === 0 ? (
        <div className="empty-state" style={{ minHeight: 300 }}>
          <UserCircle size={48} />
          <div style={{ fontSize: 15, fontWeight: 600 }}>Nenhum evento criado</div>
          <div style={{ fontSize: 13 }}>
            <button className="btn btn-primary btn-sm" onClick={() => navigate('/events/create')}>
              Criar Primeiro Evento
            </button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {myEvents.map((event) => {
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
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 12, color: 'var(--text-muted)' }}>
                      <span>{event.rulesMode}</span>
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
