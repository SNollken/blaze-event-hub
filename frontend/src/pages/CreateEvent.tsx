import { useState } from 'react';
import { createEvent } from '../api/client';
import type { CreateEventRequest } from '../api/client';
import { useNavigate } from 'react-router-dom';

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

  const addRule = () => setRules([...rules, { actionType: 'vote', thresholdAmount: 100, entries: 3 }]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !channelId.trim()) {
      setError('Título e ID do canal são obrigatórios.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const data: CreateEventRequest = {
        title, description, creatorChannelId: channelId,
        rulesMode, maxEntriesPerParticipant: maxEntries || 0, rules,
      };
      const event = await createEvent(data);
      navigate(`/events/${event.id}`);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 24, maxWidth: 600 }}>
      <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 24 }}>Criar Evento</h1>

      {error && <div style={{ color: 'var(--danger)', marginBottom: 16, padding: 12, background: 'var(--bg-hover)', borderRadius: 8 }}>{error}</div>}

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>Título *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} required
            style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 14 }} />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>Descrição</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3}
            style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 14, resize: 'vertical' }} />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>ID do Canal Blaze *</label>
          <input value={channelId} onChange={(e) => setChannelId(e.target.value)} required placeholder="UUID do seu canal na Blaze"
            style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 14 }} />
        </div>
        <div style={{ display: 'flex', gap: 16 }}>
          <div style={{ flex: 1 }}>
            <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>Modo de Regras</label>
            <select value={rulesMode} onChange={(e) => setRulesMode(e.target.value)}
              style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 14 }}>
              <option value="tier">Tier (maior atingido)</option>
              <option value="cumulative">Acumulativo</option>
            </select>
          </div>
          <div style={{ flex: 1 }}>
            <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>Máx. Entries/Pessoa</label>
            <input type="number" value={maxEntries} onChange={(e) => setMaxEntries(Number(e.target.value))}
              style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 14 }} />
          </div>
        </div>

        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <label style={{ fontSize: 14, fontWeight: 500 }}>Regras de Entries</label>
            <button type="button" onClick={addRule}
              style={{ background: 'var(--bg-hover)', border: '1px solid var(--border)', padding: '6px 14px', borderRadius: 6, cursor: 'pointer', fontSize: 13, color: 'var(--text-primary)' }}>
              + Adicionar Regra
            </button>
          </div>
          {rules.map((r, i) => (
            <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
              <select value={r.actionType}
                onChange={(e) => { const nr = [...rules]; nr[i] = { ...nr[i], actionType: e.target.value }; setRules(nr); }}
                style={{ padding: '8px', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 13 }}>
                <option value="vote">Voto</option>
                <option value="sub">Sub</option>
              </select>
              <input type="number" value={r.thresholdAmount} placeholder="Qtd"
                onChange={(e) => { const nr = [...rules]; nr[i] = { ...nr[i], thresholdAmount: Number(e.target.value) }; setRules(nr); }}
                style={{ width: 70, padding: '8px', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 13 }} />
              <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>=</span>
              <input type="number" value={r.entries} placeholder="Entries"
                onChange={(e) => { const nr = [...rules]; nr[i] = { ...nr[i], entries: Number(e.target.value) }; setRules(nr); }}
                style={{ width: 70, padding: '8px', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text-primary)', fontSize: 13 }} />
              <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>entries</span>
              {rules.length > 1 && (
                <button type="button" onClick={() => setRules(rules.filter((_, j) => j !== i))}
                  style={{ background: 'none', border: 'none', color: 'var(--danger)', cursor: 'pointer', fontSize: 18 }}>&times;</button>
              )}
            </div>
          ))}
        </div>

        <button type="submit" disabled={loading} style={{
          background: loading ? 'var(--bg-hover)' : 'var(--primary)',
          color: '#fff', border: 'none', padding: '12px 24px', borderRadius: 8,
          fontWeight: 600, fontSize: 15, cursor: loading ? 'not-allowed' : 'pointer', marginTop: 8,
        }}>
          {loading ? 'Criando...' : 'Criar Evento'}
        </button>
      </form>
    </div>
  );
}
