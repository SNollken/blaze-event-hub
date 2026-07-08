import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  cancelEvent,
  closeEvent,
  executeDraw,
  expressInterest,
  getEntries,
  getEvent,
  getEventStats,
  getParticipants,
  getWinner,
  openEvent,
  recalculate,
  withdrawInterest,
} from '../api/client';
import type {
  EntryResponse,
  EventResponse,
  EventStatsResponse,
  ParticipantResponse,
  WinnerResponse,
} from '../api/client';

type ActionName =
  | 'open'
  | 'close'
  | 'cancel'
  | 'interest'
  | 'withdraw'
  | 'recalculate'
  | 'draw';

const numberFormatter = new Intl.NumberFormat('pt-BR');

function formatNumber(value: number | null | undefined) {
  return numberFormatter.format(value ?? 0);
}

function formatLast24h(last24h: EventStatsResponse['last24h']) {
  if (typeof last24h === 'number') {
    return formatNumber(last24h);
  }

  return `${formatNumber(last24h?.votes)} votos / ${formatNumber(last24h?.subs)} subs / ${formatNumber(last24h?.giftedSubs)} gifted`;
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Erro inesperado.';
}

function isNotFoundError(error: unknown) {
  return error instanceof Error && error.message.startsWith('API 404:');
}

function actionLabel(activeAction: ActionName | null, action: ActionName, label: string) {
  return activeAction === action ? 'Processando...' : label;
}

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [stats, setStats] = useState<EventStatsResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);
  const [entries, setEntries] = useState<EntryResponse[]>([]);
  const [winner, setWinner] = useState<WinnerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [actionLoading, setActionLoading] = useState<ActionName | null>(null);

  const load = useCallback(async (showLoading = false) => {
    if (!id) {
      setEvent(null);
      setStats(null);
      setParticipants([]);
      setEntries([]);
      setWinner(null);
      setErr('ID do evento ausente.');
      setLoading(false);
      return;
    }

    if (showLoading) setLoading(true);
    setErr('');

    try {
      const [ev, currentStats, currentParticipants, currentEntries] = await Promise.all([
        getEvent(id),
        getEventStats(id),
        getParticipants(id),
        getEntries(id),
      ]);

      setEvent(ev);
      setStats(currentStats);
      setParticipants(currentParticipants);
      setEntries(currentEntries);

      try {
        setWinner(await getWinner(id));
      } catch (error) {
        setWinner(null);
        if (!isNotFoundError(error)) {
          setErr(`Nao foi possivel carregar vencedor: ${getErrorMessage(error)}`);
        }
      }
    } catch (error) {
      setErr(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load(true);
  }, [load]);

  const runAction = async <T,>(
    action: ActionName,
    request: () => Promise<T>,
    afterRefresh?: (result: T) => void,
  ) => {
    setActionLoading(action);
    setErr('');

    try {
      const result = await request();
      await load();
      afterRefresh?.(result);
    } catch (error) {
      setErr(getErrorMessage(error));
    } finally {
      setActionLoading(null);
    }
  };

  if (loading && !event) return <div className="empty">Carregando evento...</div>;
  if (err && !event) return <div style={{ padding: 40, color: 'var(--danger)' }}>{err}</div>;
  if (!event) return <div className="empty">Evento nao encontrado.</div>;

  const statusMap: Record<string, { label: string; cls: string }> = {
    OPEN: { label: 'Aberto', cls: 'pill--open' },
    CLOSED: { label: 'Fechado', cls: 'pill--closed' },
    COMPLETED: { label: 'Concluido', cls: 'pill--completed' },
    DRAWING: { label: 'Sorteando', cls: 'pill--closed' },
    DRAFT: { label: 'Rascunho', cls: 'pill--draft' },
    CANCELLED: { label: 'Cancelado', cls: 'pill--cancelled' },
  };
  const st = statusMap[event.status] || statusMap.DRAFT;

  const activeRules = event.rules?.filter((rule) => rule.isActive) ?? [];
  const modeLabel = event.mode ?? event.rulesMode ?? 'tier';
  const maxEntries = event.maxEntries ?? event.maxEntriesPerParticipant;
  const maxLabel = maxEntries && maxEntries > 0 ? `${formatNumber(maxEntries)}/pessoa` : 'ilimitado';
  const isActionBusy = actionLoading !== null;
  const canOpen = event.status === 'DRAFT';
  const canClose = event.status === 'OPEN';
  const canCancel = event.status === 'OPEN' || event.status === 'DRAFT';
  const canRecalculate = event.status === 'CLOSED';
  const canDraw = event.status === 'CLOSED' && !winner;
  const statItems = stats ? [
    { label: 'Total votes', value: formatNumber(stats.totalVotes) },
    { label: 'Total subs', value: formatNumber(stats.totalSubs) },
    { label: 'Gifted subs', value: formatNumber(stats.totalGiftedSubs) },
    { label: 'Participantes', value: formatNumber(stats.participants) },
    { label: 'Total entries', value: formatNumber(stats.totalEntries) },
    { label: 'Last 24h', value: formatLast24h(stats.last24h), compact: true },
  ] : [];

  return (
    <div style={{ padding: '32px 40px', maxWidth: 920 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 24 }}>
        <div>
          <h1 className="page-title">{event.title}</h1>
          {event.description && (
            <p className="page-subtitle" style={{ marginBottom: 0 }}>
              {event.description}
            </p>
          )}
          <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 8, fontFamily: 'var(--font-mono)' }}>
            Modo: {modeLabel} - Max: {maxLabel} - Regras: {activeRules.length} ativa{activeRules.length !== 1 ? 's' : ''}
          </div>
        </div>
        <span className={`pill ${st.cls}`}>{st.label}</span>
      </div>

      {err && (
        <div style={{
          marginBottom: 16,
          padding: '8px 12px',
          borderRadius: 'var(--r)',
          background: 'var(--danger-bg)',
          color: 'var(--danger)',
          fontSize: 13,
        }}>
          {err}
        </div>
      )}

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 32 }}>
        <button className="btn btn-secondary" disabled={isActionBusy} onClick={() => navigate(`/events/${event.id}/edit`)}>
          Editar
        </button>
        {event.status === 'OPEN' && (
          <>
            <button
              className="btn btn-primary"
              disabled={isActionBusy}
              onClick={() => void runAction('interest', () => expressInterest(event.id))}
            >
              {actionLabel(actionLoading, 'interest', 'Participar')}
            </button>
            <button
              className="btn btn-secondary"
              disabled={isActionBusy}
              onClick={() => void runAction('withdraw', () => withdrawInterest(event.id))}
            >
              {actionLabel(actionLoading, 'withdraw', 'Remover interesse')}
            </button>
          </>
        )}
        <button
          className="btn btn-success"
          disabled={isActionBusy || !canOpen}
          onClick={() => void runAction('open', () => openEvent(event.id))}
        >
          {actionLabel(actionLoading, 'open', 'Abrir')}
        </button>
        <button
          className="btn btn-warning"
          disabled={isActionBusy || !canClose}
          onClick={() => void runAction('close', () => closeEvent(event.id))}
        >
          {actionLabel(actionLoading, 'close', 'Fechar')}
        </button>
        <button
          className="btn btn-danger"
          disabled={isActionBusy || !canCancel}
          onClick={() => void runAction('cancel', () => cancelEvent(event.id))}
        >
          {actionLabel(actionLoading, 'cancel', 'Cancelar')}
        </button>
        <button
          className="btn btn-secondary"
          disabled={isActionBusy || !canRecalculate}
          onClick={() => void runAction('recalculate', () => recalculate(event.id))}
        >
          {actionLabel(actionLoading, 'recalculate', 'Recalcular')}
        </button>
        <button
          className="btn btn-primary"
          disabled={isActionBusy || !canDraw}
          onClick={() => void runAction('draw', () => executeDraw(event.id), setWinner)}
        >
          {actionLabel(actionLoading, 'draw', 'Sortear')}
        </button>
      </div>

      <section style={{ marginBottom: 32 }}>
        <div className="section-label">Stats</div>
        {stats ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 8 }}>
            {statItems.map((item) => (
              <div key={item.label} className="card" style={{ minHeight: 72 }}>
                <div style={{ fontSize: 11, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 8 }}>
                  {item.label}
                </div>
                <div style={{ fontSize: item.compact ? 13 : 20, fontWeight: 600, color: 'var(--fg)', lineHeight: 1.35 }}>
                  {item.value}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="empty">Stats indisponiveis.</div>
        )}
      </section>

      {activeRules.length > 0 && (
        <section style={{ marginBottom: 32 }}>
          <div className="section-label">Regras</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {activeRules.map((rule) => (
              <div key={rule.id} className="card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--fg2)' }}>
                  {formatNumber(rule.thresholdAmount)} {rule.actionType === 'vote' ? 'votos' : 'subs'}
                </span>
                <span style={{ color: 'var(--fg)', fontWeight: 510 }}>
                  {formatNumber(rule.entries)} {rule.entries === 1 ? 'entry' : 'entries'}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {winner && (
        <div className="winner-box">
          <div className="section-label">Vencedor</div>
          <div className="winner-name">{winner.memberId}</div>
          <div className="winner-meta">
            {formatNumber(winner.entriesAtDrawTime)} entries - {winner.drawMethod} - seed {winner.drawSeed}
          </div>
        </div>
      )}

      <section style={{ marginBottom: 32 }}>
        <div className="section-label">
          Participantes <span className="count">({participants.length})</span>
        </div>
        {participants.length === 0 ? (
          <div className="empty">Ninguem demonstrou interesse ainda.</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {participants.map((participant) => (
              <div key={participant.memberId} className="card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
                <span style={{ fontSize: 13, color: 'var(--fg)' }}>
                  {participant.displayName || participant.blazeUsername || participant.memberId}
                </span>
                <span style={{ fontSize: 12, color: 'var(--muted)', fontFamily: 'var(--font-mono)' }}>
                  {formatNumber(participant.lastCalculatedEntries)} entries
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <div className="section-label">
          Entries <span className="count">({entries.length})</span>
        </div>
        {entries.length === 0 ? (
          <div className="empty">Nenhuma entrada registrada.</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {entries.map((entry) => (
              <div key={entry.id} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, marginBottom: 2 }}>
                  <span style={{ fontSize: 13, fontWeight: 510, color: 'var(--fg)' }}>
                    {formatNumber(entry.amount)} {entry.actionType}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 510, color: 'var(--accent-light)' }}>
                    {formatNumber(entry.entriesGranted)} entries
                  </span>
                </div>
                <div style={{ fontSize: 11, color: 'var(--muted)', fontFamily: 'var(--font-mono)' }}>
                  {entry.calculationReason}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
