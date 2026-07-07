import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getEvent, openEvent, closeEvent, cancelEvent, expressInterest, withdrawInterest, getParticipants, getEntries, recalculate, executeDraw, getWinner } from '../api/client';
import type { EventResponse, ParticipantResponse, EntryResponse, WinnerResponse } from '../api/client';

const btnGhost: React.CSSProperties = {
  background: 'var(--bg-button)', border: '1px solid var(--border-card)',
  color: 'var(--text-secondary)', padding: '7px 14px', borderRadius: 'var(--radius)',
  fontWeight: 510, fontSize: 13, cursor: 'pointer',
};
const btnBrand: React.CSSProperties = { ...btnGhost, background: 'var(--brand)', color: '#fff', border: 'none' };

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);
  const [entries, setEntries] = useState<EntryResponse[]>([]);
  const [winner, setWinner] = useState<WinnerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');

  const load = async () => {
    if (!id) return;
    try {
      const [ev, p, e] = await Promise.all([getEvent(id), getParticipants(id), getEntries(id)]);
      setEvent(ev); setParticipants(p); setEntries(e);
      try { setWinner(await getWinner(id)); } catch { setWinner(null); }
    } catch (e: any) { setErr(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, [id]);

  const act = async (fn: () => Promise<any>) => {
    setErr('');
    try { await fn(); await load(); } catch (e: any) { setErr(e.message); }
  };

  if (loading) return <div style={{ padding: 40, color: 'var(--text-muted)' }}>Carregando...</div>;
  if (err && !event) return <div style={{ padding: 40, color: 'var(--danger)' }}>{err}</div>;
  if (!event) return <div style={{ padding: 40, color: 'var(--text-muted)' }}>Nao encontrado.</div>;

  const sc: Record<string, { c: string; bg: string; l: string }> = {
    OPEN: { c: 'var(--success)', bg: 'var(--success-bg)', l: 'Aberto' },
    CLOSED: { c: 'var(--warning)', bg: 'var(--warning-bg)', l: 'Fechado' },
    COMPLETED: { c: 'var(--brand-light)', bg: 'var(--brand-bg)', l: 'Concluido' },
    DRAFT: { c: 'var(--text-muted)', bg: 'rgba(255,255,255,0.04)', l: 'Rascunho' },
    CANCELLED: { c: 'var(--danger)', bg: 'var(--danger-bg)', l: 'Cancelado' },
  };
  const s = sc[event.status] || sc.DRAFT;

  return (
    <div style={{ padding: '32px 40px', maxWidth: 700 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 20, fontWeight: 510, color: 'var(--text-primary)', letterSpacing: '-0.3px' }}>
            {event.title}
          </h1>
          {event.description && (
            <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 4, lineHeight: 1.5 }}>
              {event.description}
            </p>
          )}
        </div>
        <span style={{
          fontSize: 11, fontWeight: 510, color: s.c, background: s.bg,
          padding: '3px 10px', borderRadius: 'var(--radius-full)', whiteSpace: 'nowrap',
        }}>{s.l}</span>
      </div>

      {err && (
        <div style={{
          marginBottom: 16, padding: '8px 12px', borderRadius: 'var(--radius)',
          background: 'var(--danger-bg)', color: 'var(--danger)', fontSize: 13,
        }}>{err}</div>
      )}

      {/* Actions */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 32 }}>
        {event.status === 'OPEN' && (
          <>
            <button style={btnBrand} onClick={() => act(() => expressInterest(event.id))}>Participar</button>
            <button style={btnGhost} onClick={() => act(() => withdrawInterest(event.id))}>Remover interesse</button>
          </>
        )}
        {event.status === 'DRAFT' && (
          <button style={{ ...btnGhost, color: 'var(--success)' }} onClick={() => act(() => openEvent(event.id))}>Abrir</button>
        )}
        {event.status === 'OPEN' && (
          <button style={{ ...btnGhost, color: 'var(--warning)' }} onClick={() => act(() => closeEvent(event.id))}>Fechar</button>
        )}
        {(event.status === 'OPEN' || event.status === 'DRAFT') && (
          <button style={{ ...btnGhost, color: 'var(--danger)' }} onClick={() => act(() => cancelEvent(event.id))}>Cancelar</button>
        )}
        {event.status === 'CLOSED' && !winner && (
          <>
            <button style={btnGhost} onClick={() => act(() => recalculate(event.id))}>Recalcular</button>
            <button style={{ ...btnBrand, background: 'var(--brand-hover)' }} onClick={() => act(() => executeDraw(event.id))}>Sortear</button>
          </>
        )}
      </div>

      {/* Rules */}
      {event.rules && event.rules.length > 0 && (
        <section style={{ marginBottom: 32 }}>
          <SectionTitle>Regras</SectionTitle>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {event.rules.filter(r => r.isActive).map(r => (
              <div key={r.id} style={{
                fontSize: 13, padding: '10px 14px', background: 'var(--bg-card)',
                border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              }}>
                <span style={{ color: 'var(--text-secondary)' }}>
                  {r.thresholdAmount} {r.actionType === 'vote' ? 'votos' : 'subs'}
                </span>
                <span style={{ color: 'var(--text-primary)', fontWeight: 510 }}>
                  {r.entries} {r.entries === 1 ? 'entry' : 'entries'}
                </span>
              </div>
            ))}
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6, fontFamily: 'var(--font-mono)' }}>
            Modo: {event.rulesMode}{event.maxEntriesPerParticipant > 0 && ` · Max: ${event.maxEntriesPerParticipant}/pessoa`}
          </div>
        </section>
      )}

      {/* Winner */}
      {winner && (
        <section style={{
          marginBottom: 32, padding: '16px 20px',
          background: 'rgba(94,106,210,0.06)', border: '1px solid rgba(94,106,210,0.15)',
          borderRadius: 'var(--radius-lg)',
        }}>
          <SectionTitle>Vencedor</SectionTitle>
          <div style={{ fontSize: 14, color: 'var(--text-primary)', fontWeight: 510 }}>{winner.memberId}</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2, fontFamily: 'var(--font-mono)' }}>
            {winner.entriesAtDrawTime} entries · {winner.drawMethod} · seed {winner.drawSeed}
          </div>
        </section>
      )}

      {/* Participants */}
      <section style={{ marginBottom: 32 }}>
        <SectionTitle>Participantes ({participants.length})</SectionTitle>
        {participants.length === 0 ? (
          <Empty>Ninguem demonstrou interesse ainda.</Empty>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {participants.map(p => (
              <div key={p.memberId} style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                padding: '10px 14px', background: 'var(--bg-card)',
                border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
              }}>
                <span style={{ fontSize: 13, color: 'var(--text-primary)' }}>{p.displayName || p.blazeUsername}</span>
                <span style={{ fontSize: 12, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                  {p.lastCalculatedEntries ?? 0} entries
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Entries */}
      <section>
        <SectionTitle>Entries ({entries.length})</SectionTitle>
        {entries.length === 0 ? (
          <Empty>Nenhuma entrada registrada.</Empty>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {entries.map(e => (
              <div key={e.id} style={{
                padding: '10px 14px', background: 'var(--bg-card)',
                border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
                  <span style={{ fontSize: 13, fontWeight: 510, color: 'var(--text-primary)' }}>
                    {e.amount} {e.actionType}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 510, color: 'var(--brand-light)' }}>
                    {e.entriesGranted} entries
                  </span>
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                  {e.calculationReason}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <div style={{ fontSize: 11, fontWeight: 510, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>{children}</div>;
}
function Empty({ children }: { children: React.ReactNode }) {
  return <div style={{ fontSize: 13, color: 'var(--text-muted)', padding: '20px 0', textAlign: 'center' }}>{children}</div>;
}
