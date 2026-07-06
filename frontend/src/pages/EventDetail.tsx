import { useCallback, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { Badge } from '../components/Badge';
import { usePolling, addToast } from '../components/Toast';
import {
  getEvent,
  getEntries,
  getParticipants,
  openEvent,
  closeEvent,
  cancelEvent,
  expressInterest,
  withdrawInterest,
  recalculate,
  executeDraw,
  getWinner,
} from '../api/client';
import { ArrowLeft, Users, Trophy, Zap, XCircle, Play, Square, Trash2 } from 'lucide-react';
import type { EventResponse, EntryResponse, ParticipantResponse, WinnerResponse } from '../api/types';

const statusBadge: Record<string, { variant: 'success' | 'warning' | 'error' | 'neutral'; label: string }> = {
  DRAFT: { variant: 'neutral', label: 'Rascunho' },
  OPEN: { variant: 'success', label: 'Aberto' },
  CLOSED: { variant: 'warning', label: 'Fechado' },
  DRAWING: { variant: 'warning', label: 'Sorteando' },
  COMPLETED: { variant: 'success', label: 'Concluido' },
  CANCELLED: { variant: 'error', label: 'Cancelado' },
};

function formatDate(s: string) {
  return new Date(s).toLocaleString('pt-BR');
}

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const fetchEvent = useCallback(() => getEvent(id!), [id]);
  const fetchEntries = useCallback(() => getEntries(id!), [id]);
  const fetchParticipants = useCallback(() => getParticipants(id!), [id]);
  const fetchWinner = useCallback(() => getWinner(id!), [id]);

  const { data: event, loading, error, reload: reloadEvent } = usePolling(fetchEvent, 5000);
  const { data: entries, reload: reloadEntries } = usePolling(fetchEntries, 5000);
  const { data: participants } = usePolling(fetchParticipants, 5000);
  const { data: winner } = usePolling(fetchWinner, 10000);

  if (!id) return null;

  async function withAction(label: string, fn: () => Promise<unknown>) {
    setActionLoading(label);
    try {
      await fn();
      addToast('success', `${label} com sucesso`);
      reloadEvent();
      reloadEntries();
    } catch (err) {
      addToast('error', err instanceof Error ? err.message : `Erro ao ${label.toLowerCase()}`);
    } finally {
      setActionLoading(null);
    }
  }

  if (loading && !event) {
    return (
      <Layout title="Detalhes do Evento">
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div>Carregando evento...</div>
        </div>
      </Layout>
    );
  }

  if (error || !event) {
    return (
      <Layout title="Detalhes do Evento">
        <div className="empty-state" style={{ minHeight: 300 }}>
          <div style={{ color: 'var(--error)' }}>{error || 'Evento nao encontrado'}</div>
          <button className="btn btn-secondary btn-sm" onClick={() => navigate('/events')}>
            Voltar
          </button>
        </div>
      </Layout>
    );
  }

  const status = statusBadge[event.status] ?? statusBadge.DRAFT;

  return (
    <Layout
      title={event.title}
      subtitle={`Evento #${event.id.slice(0, 8)}`}
      headerActions={
        <Badge variant={status.variant} dot>
          {status.label}
        </Badge>
      }
    >
      <button
        className="btn btn-secondary btn-sm"
        onClick={() => navigate('/events')}
        style={{ marginBottom: 20 }}
      >
        <ArrowLeft size={14} />
        Voltar
      </button>

      <div className="responsive-grid-2" style={{ marginBottom: 24 }}>
        {/* Info card */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Informacoes</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-secondary)' }}>Status</span>
              <Badge variant={status.variant} dot>{status.label}</Badge>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-secondary)' }}>Modo de Regras</span>
              <span>{event.rulesMode}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-secondary)' }}>Max Entries/Participante</span>
              <span>{event.maxEntriesPerParticipant}</span>
            </div>
            {event.startsAt && (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Inicio</span>
                <span>{formatDate(event.startsAt)}</span>
              </div>
            )}
            {event.endsAt && (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Fim</span>
                <span>{formatDate(event.endsAt)}</span>
              </div>
            )}
          </div>
          {event.description && (
            <div style={{ marginTop: 16, padding: '12px 14px', background: 'var(--bg-base)', borderRadius: 'var(--radius)', fontSize: 13, color: 'var(--text-secondary)' }}>
              {event.description}
            </div>
          )}
          {event.rules && event.rules.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 8 }}>Regras</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {event.rules.map((rule) => (
                  <div
                    key={rule.id}
                    style={{
                      padding: '8px 12px',
                      border: '1px solid var(--border)',
                      borderRadius: 'var(--radius-sm)',
                      fontSize: 12,
                      display: 'flex',
                      justifyContent: 'space-between',
                    }}
                  >
                    <span>{rule.actionType} x{rule.thresholdAmount}</span>
                    <span style={{ color: 'var(--primary)' }}>{rule.entries} entries</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Actions card */}
        <div className="glass-card" style={{ padding: 20 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Acoes</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {event.status === 'DRAFT' && (
              <button
                className="btn btn-primary"
                onClick={() => withAction('Abrir evento', () => openEvent(id))}
                disabled={actionLoading === 'Abrir evento'}
              >
                <Play size={14} />
                {actionLoading === 'Abrir evento' ? 'Abrindo...' : 'Abrir Evento'}
              </button>
            )}
            {event.status === 'OPEN' && (
              <>
                <button
                  className="btn btn-secondary"
                  onClick={() => withAction('Adicionar interesse', () => expressInterest(id))}
                  disabled={actionLoading === 'Adicionar interesse'}
                >
                  <Users size={14} />
                  {actionLoading === 'Adicionar interesse' ? 'Adicionando...' : 'Participar'}
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={() => withAction('Remover interesse', () => withdrawInterest(id))}
                  disabled={actionLoading === 'Remover interesse'}
                >
                  <XCircle size={14} />
                  {actionLoading === 'Remover interesse' ? 'Removendo...' : 'Sair do Evento'}
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={() => withAction('Recalcular entries', () => recalculate(id))}
                  disabled={actionLoading === 'Recalcular entries'}
                >
                  <Zap size={14} />
                  {actionLoading === 'Recalcular entries' ? 'Recalculando...' : 'Recalcular Entries'}
                </button>
                <button
                  className="btn btn-accent"
                  onClick={() => withAction('Sortear', () => executeDraw(id))}
                  disabled={actionLoading === 'Sortear'}
                >
                  <Trophy size={14} />
                  {actionLoading === 'Sortear' ? 'Sorteando...' : 'Sortear Vencedor'}
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={() => withAction('Fechar evento', () => closeEvent(id))}
                  disabled={actionLoading === 'Fechar evento'}
                >
                  <Square size={14} />
                  {actionLoading === 'Fechar evento' ? 'Fechando...' : 'Fechar Evento'}
                </button>
              </>
            )}
            {event.status === 'CLOSED' && (
              <button
                className="btn btn-accent"
                onClick={() => withAction('Sortear', () => executeDraw(id))}
                disabled={actionLoading === 'Sortear'}
              >
                <Trophy size={14} />
                {actionLoading === 'Sortear' ? 'Sorteando...' : 'Sortear Vencedor'}
              </button>
            )}
            {event.status !== 'CANCELLED' && event.status !== 'COMPLETED' && (
              <button
                className="btn btn-danger"
                onClick={() => withAction('Cancelar evento', () => cancelEvent(id))}
                disabled={actionLoading === 'Cancelar evento'}
              >
                <Trash2 size={14} />
                {actionLoading === 'Cancelar evento' ? 'Cancelando...' : 'Cancelar Evento'}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Winner */}
      {winner && event.status === 'COMPLETED' && (
        <div
          className="glass-card"
          style={{
            padding: 20,
            marginBottom: 24,
            borderColor: 'var(--accent)',
            background: 'var(--accent-subtle)',
          }}
        >
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: 'var(--accent)' }}>
            <Trophy size={16} style={{ verticalAlign: 'middle', marginRight: 6 }} />
            Vencedor
          </h3>
          <div style={{ fontSize: 16, fontWeight: 700 }}>{winner.memberId}</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
            Entries no sorteio: {winner.entriesAtDrawTime} | Metodo: {winner.drawMethod}
          </div>
        </div>
      )}

      {/* Participants */}
      <div className="glass-card" style={{ padding: 20, marginBottom: 16 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>
          Participantes ({participants?.length ?? 0})
        </h3>
        {!participants || participants.length === 0 ? (
          <div className="empty-state" style={{ padding: 24 }}>
            <Users size={32} />
            <div style={{ fontSize: 13 }}>Nenhum participante ainda</div>
          </div>
        ) : (
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Username</th>
                  <th>Status</th>
                  <th>Entries</th>
                </tr>
              </thead>
              <tbody>
                {participants.map((p: ParticipantResponse) => (
                  <tr key={p.memberId}>
                    <td style={{ fontWeight: 500 }}>{p.displayName}</td>
                    <td className="mono">@{p.blazeUsername}</td>
                    <td>
                      <Badge variant={p.status === 'ACTIVE' ? 'success' : 'neutral'} dot>
                        {p.status}
                      </Badge>
                    </td>
                    <td>{p.lastCalculatedEntries}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Entries */}
      <div className="glass-card" style={{ padding: 20 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>
          Entries ({entries?.length ?? 0})
        </h3>
        {!entries || entries.length === 0 ? (
          <div className="empty-state" style={{ padding: 24 }}>
            <Zap size={32} />
            <div style={{ fontSize: 13 }}>Nenhum entry registrado</div>
          </div>
        ) : (
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Acao</th>
                  <th>Amount</th>
                  <th>Entries</th>
                  <th>Motivo</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((e: EntryResponse) => (
                  <tr key={e.id}>
                    <td style={{ fontWeight: 500 }}>{e.actionType}</td>
                    <td>{e.amount}</td>
                    <td style={{ color: 'var(--primary)', fontWeight: 600 }}>+{e.entriesGranted}</td>
                    <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>{e.calculationReason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Layout>
  );
}
