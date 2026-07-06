import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getEvent, openEvent, closeEvent, cancelEvent, expressInterest, withdrawInterest, getParticipants, getEntries, recalculate, executeDraw, getWinner } from '../api/client';
import type { EventResponse, ParticipantResponse, EntryResponse, WinnerResponse } from '../api/client';

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);
  const [entries, setEntries] = useState<EntryResponse[]>([]);
  const [winner, setWinner] = useState<WinnerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const load = async () => {
    if (!id) return;
    try {
      const [ev, p, e] = await Promise.all([getEvent(id), getParticipants(id), getEntries(id)]);
      setEvent(ev); setParticipants(p); setEntries(e);
      try { const w = await getWinner(id); setWinner(w); } catch { setWinner(null); }
    } catch (e: any) { setError(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, [id]);

  const doAction = async (fn: () => Promise<any>) => {
    setActionError('');
    try { await fn(); await load(); }
    catch (e: any) { setActionError(e.message); }
  };

  if (loading) return <div style={{ padding: 24 }}>Carregando evento...</div>;
  if (error) return <div style={{ padding: 24, color: 'var(--danger)' }}>Erro: {error}</div>;
  if (!event) return <div style={{ padding: 24 }}>Evento não encontrado.</div>;

  const statusColor = event.status === 'OPEN' ? 'var(--success)' : event.status === 'CLOSED' ? 'var(--warning)' : event.status === 'COMPLETED' ? 'var(--accent)' : 'var(--text-secondary)';

  return (
    <div style={{ padding: 24, maxWidth: 700 }}>
      {actionError && <div style={{ color: 'var(--danger)', marginBottom: 16, padding: 12, background: 'var(--bg-hover)', borderRadius: 8 }}>{actionError}</div>}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700 }}>{event.title}</h1>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 4 }}>{event.description || 'Sem descrição'}</p>
        </div>
        <span style={{ padding: '4px 12px', borderRadius: 20, fontSize: 13, fontWeight: 600, background: statusColor, color: '#fff' }}>
          {event.status}
        </span>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 24, flexWrap: 'wrap' }}>
        {event.status === 'OPEN' && (
          <>
            <button onClick={() => doAction(() => expressInterest(event.id))} style={btnStyle('var(--primary)')}>
              Quero Participar
            </button>
            <button onClick={() => doAction(() => withdrawInterest(event.id))} style={btnStyle('var(--bg-hover)')}>
              Remover Interesse
            </button>
          </>
        )}
        {event.status === 'DRAFT' && (
          <button onClick={() => doAction(() => openEvent(event.id))} style={btnStyle('var(--success)')}>
            Abrir Evento
          </button>
        )}
        {event.status === 'OPEN' && (
          <button onClick={() => doAction(() => closeEvent(event.id))} style={btnStyle('var(--warning)')}>
            Fechar Evento
          </button>
        )}
        {(event.status === 'OPEN' || event.status === 'DRAFT') && (
          <button onClick={() => doAction(() => cancelEvent(event.id))} style={btnStyle('var(--danger)')}>
            Cancelar
          </button>
        )}
        {event.status === 'CLOSED' && !winner && (
          <>
            <button onClick={() => doAction(() => recalculate(event.id))} style={btnStyle('var(--bg-hover)')}>
              Recalcular Entries
            </button>
            <button onClick={() => doAction(() => executeDraw(event.id))} style={btnStyle('var(--accent)')}>
              Sortear Vencedor
            </button>
          </>
        )}
      </div>

      {/* Regras */}
      {event.rules && event.rules.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>Regras</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {event.rules.filter(r => r.isActive).map((r) => (
              <div key={r.id} style={{ fontSize: 14, padding: '8px 12px', background: 'var(--bg-hover)', borderRadius: 8 }}>
                {r.thresholdAmount} {r.actionType}s = {r.entries} entries
              </div>
            ))}
          </div>
          <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>
            Modo: {event.rulesMode} {event.maxEntriesPerParticipant > 0 ? `· Máx: ${event.maxEntriesPerParticipant} entries/pessoa` : ''}
          </p>
        </div>
      )}

      {/* Vencedor */}
      {winner && (
        <div style={{ marginBottom: 24, padding: 16, background: 'var(--bg-hover)', borderRadius: 12, border: '2px solid var(--accent)' }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--accent)', marginBottom: 4 }}>Vencedor!</h3>
          <p style={{ fontSize: 14 }}>Membro: {winner.memberId}</p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Entries: {winner.entriesAtDrawTime} · Método: {winner.drawMethod}</p>
        </div>
      )}

      {/* Participantes */}
      <div style={{ marginBottom: 24 }}>
        <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>Participantes ({participants.length})</h3>
        {participants.length === 0 ? (
          <p style={{ color: 'var(--text-secondary)', fontSize: 14 }}>Ninguém demonstrou interesse ainda.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {participants.map((p) => (
              <div key={p.memberId} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--bg-card)', borderRadius: 8, border: '1px solid var(--border)', fontSize: 14 }}>
                <span>{p.displayName || p.blazeUsername}</span>
                <span style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{p.lastCalculatedEntries} entries</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Entries */}
      <div>
        <h3 style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>Entradas ({entries.length})</h3>
        {entries.length === 0 ? (
          <p style={{ color: 'var(--text-secondary)', fontSize: 14 }}>Nenhuma entrada registrada.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {entries.map((e) => (
              <div key={e.id} style={{ padding: '8px 12px', background: 'var(--bg-card)', borderRadius: 8, border: '1px solid var(--border)', fontSize: 13 }}>
                <strong>{e.actionType}</strong> · {e.amount} ação(ões) = {e.entriesGranted} entries
                <div style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{e.calculationReason}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function btnStyle(bg: string): React.CSSProperties {
  return {
    background: bg, color: bg.includes('hover') ? 'var(--text-primary)' : '#fff',
    border: '1px solid var(--border)', padding: '8px 16px', borderRadius: 8,
    cursor: 'pointer', fontSize: 13, fontWeight: 600,
  };
}
