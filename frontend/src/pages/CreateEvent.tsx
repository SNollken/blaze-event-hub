import { useState } from 'react';
import { createEvent } from '../api/client';
import type { CreateEventRequest } from '../api/client';
import { useNavigate } from 'react-router-dom';

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '10px 12px', borderRadius: 'var(--radius)',
  border: '1px solid var(--border-input)', background: 'var(--bg-input)',
  color: 'var(--text-primary)', fontSize: 14, outline: 'none',
};
const labelStyle: React.CSSProperties = {
  fontSize: 12, fontWeight: 510, color: 'var(--text-muted)',
  textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6, display: 'block',
};

export default function CreateEvent() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [channelId, setChannelId] = useState('');
  const [rulesMode, setRulesMode] = useState('tier');
  const [maxEntries, setMaxEntries] = useState(0);
  const [rules, setRules] = useState([{ actionType: 'vote', thresholdAmount: 50, entries: 1 }]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const updateRule = (i: number, key: string, val: string | number) => {
    const nr = [...rules];
    (nr[i] as any)[key] = val;
    setRules(nr);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !channelId.trim()) { setError('Titulo e canal obrigatorios.'); return; }
    setLoading(true); setError('');
    try {
      const event = await createEvent({
        title, description, creatorChannelId: channelId,
        rulesMode, maxEntriesPerParticipant: maxEntries || 0, rules,
      });
      navigate(`/events/${event.id}`);
    } catch (e: any) { setError(e.message); }
    finally { setLoading(false); }
  };

  return (
    <div style={{ padding: '32px 40px', maxWidth: 560 }}>
      <h1 style={{ fontSize: 20, fontWeight: 510, color: 'var(--text-primary)', letterSpacing: '-0.3px', marginBottom: 28 }}>
        Criar evento
      </h1>

      {error && (
        <div style={{
          marginBottom: 20, padding: '10px 14px', borderRadius: 'var(--radius)',
          background: 'var(--danger-bg)', color: 'var(--danger)', fontSize: 13,
          border: '1px solid rgba(239,68,68,0.15)',
        }}>{error}</div>
      )}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div>
          <label style={labelStyle}>Titulo</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} required
            placeholder="Ex: Giveaway 50 Votos" style={inputStyle} />
        </div>

        <div>
          <label style={labelStyle}>Descricao</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3}
            placeholder="Descreva as regras do evento para os participantes"
            style={{ ...inputStyle, resize: 'vertical', lineHeight: 1.5 }} />
        </div>

        <div>
          <label style={labelStyle}>Canal Blaze (ID)</label>
          <input value={channelId} onChange={(e) => setChannelId(e.target.value)} required
            placeholder="UUID do canal" style={inputStyle} />
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
            O sistema monitora votos neste canal automaticamente
          </div>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>Modo</label>
            <select value={rulesMode} onChange={(e) => setRulesMode(e.target.value)}
              style={{ ...inputStyle, appearance: 'none', cursor: 'pointer' }}>
              <option value="tier">Tier — maior marco</option>
              <option value="cumulative">Acumulativo</option>
            </select>
          </div>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>Max entries/pessoa</label>
            <input type="number" min={0} value={maxEntries || ''}
              onChange={(e) => setMaxEntries(Number(e.target.value))}
              placeholder="0 = ilimitado" style={inputStyle} />
          </div>
        </div>

        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <label style={{ ...labelStyle, marginBottom: 0 }}>Regras de entries</label>
            <button type="button" onClick={() => setRules([...rules, { actionType: 'vote', thresholdAmount: 100, entries: 3 }])}
              style={{
                background: 'var(--bg-button)', border: '1px solid var(--border-card)',
                padding: '4px 12px', borderRadius: 'var(--radius)',
                cursor: 'pointer', fontSize: 12, fontWeight: 510, color: 'var(--text-secondary)',
              }}>
              + Adicionar
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {rules.map((r, i) => (
              <div key={i} style={{
                display: 'flex', gap: 8, alignItems: 'center',
                padding: '10px 12px', background: 'var(--bg-card)',
                border: '1px solid var(--border-card)', borderRadius: 'var(--radius-md)',
              }}>
                <input type="number" value={r.thresholdAmount}
                  onChange={(e) => updateRule(i, 'thresholdAmount', Number(e.target.value))}
                  style={{ width: 60, ...inputStyle, padding: '6px 8px', textAlign: 'center' }} />
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                  {r.actionType === 'vote' ? 'votos' : 'subs'}
                </span>
                <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>=</span>
                <input type="number" value={r.entries}
                  onChange={(e) => updateRule(i, 'entries', Number(e.target.value))}
                  style={{ width: 50, ...inputStyle, padding: '6px 8px', textAlign: 'center' }} />
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>entries</span>
                {rules.length > 1 && (
                  <button type="button" onClick={() => setRules(rules.filter((_, j) => j !== i))}
                    style={{
                      marginLeft: 'auto', background: 'none', border: 'none',
                      color: 'var(--text-muted)', cursor: 'pointer', fontSize: 14, padding: 4,
                    }}>x</button>
                )}
              </div>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10, paddingTop: 8 }}>
          <button type="submit" disabled={loading} style={{
            background: loading ? 'rgba(255,255,255,0.04)' : 'var(--brand)',
            color: '#fff', border: 'none', padding: '9px 24px', borderRadius: 'var(--radius)',
            fontWeight: 510, fontSize: 13, cursor: loading ? 'not-allowed' : 'pointer',
          }}>
            {loading ? 'Criando...' : 'Criar evento'}
          </button>
          <a href="/" style={{
            padding: '9px 24px', borderRadius: 'var(--radius)',
            border: '1px solid var(--border-card)', background: 'var(--bg-button)',
            color: 'var(--text-secondary)', fontWeight: 510, fontSize: 13,
            textDecoration: 'none', display: 'flex', alignItems: 'center',
          }}>
            Cancelar
          </a>
        </div>
      </form>
    </div>
  );
}
