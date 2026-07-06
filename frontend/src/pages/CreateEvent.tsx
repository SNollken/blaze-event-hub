import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { addToast } from '../components/Toast';
import { createEvent } from '../api/client';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import type { CreateRuleRequest } from '../api/types';

export default function CreateEvent() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [rulesMode, setRulesMode] = useState('FIXED');
  const [maxEntriesPerParticipant, setMaxEntriesPerParticipant] = useState('3');
  const [rules, setRules] = useState<CreateRuleRequest[]>([
    { actionType: 'FOLLOW', thresholdAmount: 1, entries: 1 },
  ]);
  const [submitting, setSubmitting] = useState(false);

  const addRule = () => {
    setRules([...rules, { actionType: 'SUBSCRIBE', thresholdAmount: 1, entries: 3 }]);
  };

  const removeRule = (idx: number) => {
    setRules(rules.filter((_, i) => i !== idx));
  };

  const updateRule = (idx: number, field: keyof CreateRuleRequest, value: string | number) => {
    const updated = [...rules];
    updated[idx] = { ...updated[idx], [field]: value };
    setRules(updated);
  };

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      addToast('warning', 'Digite um titulo para o evento');
      return;
    }
    setSubmitting(true);
    try {
      const event = await createEvent({
        title: title.trim(),
        description: description.trim(),
        rulesMode,
        maxEntriesPerParticipant: parseInt(maxEntriesPerParticipant) || 3,
        creatorChannelId: '',
        rules,
      });
      addToast('success', 'Evento criado com sucesso');
      navigate(`/events/${event.id}`);
    } catch (err) {
      addToast('error', err instanceof Error ? err.message : 'Erro ao criar evento');
    } finally {
      setSubmitting(false);
    }
  }, [title, description, rulesMode, maxEntriesPerParticipant, rules, navigate]);

  return (
    <Layout title="Criar Evento" subtitle="Novo evento de sorteio">
      <div style={{ maxWidth: 640 }}>
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => navigate('/events')}
          style={{ marginBottom: 20 }}
        >
          <ArrowLeft size={14} />
          Voltar
        </button>

        <form onSubmit={handleSubmit} className="glass-card" style={{ padding: 24 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <label htmlFor="title">Titulo do Evento *</label>
              <input
                id="title"
                className="input"
                placeholder="Ex: Sorteio de aniversario"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                autoFocus
              />
            </div>

            <div>
              <label htmlFor="description">Descricao</label>
              <textarea
                id="description"
                className="input"
                placeholder="Descreva o evento e suas regras..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                style={{ resize: 'vertical' }}
              />
            </div>

            <div className="responsive-grid-2">
              <div>
                <label htmlFor="rulesMode">Modo de Regras</label>
                <select
                  id="rulesMode"
                  className="select"
                  value={rulesMode}
                  onChange={(e) => setRulesMode(e.target.value)}
                >
                  <option value="FIXED">Fixo</option>
                  <option value="MULTIPLIER">Multiplicador</option>
                </select>
              </div>
              <div>
                <label htmlFor="maxEntries">Max Entries por Participante</label>
                <input
                  id="maxEntries"
                  className="input"
                  type="number"
                  min="1"
                  value={maxEntriesPerParticipant}
                  onChange={(e) => setMaxEntriesPerParticipant(e.target.value)}
                />
              </div>
            </div>

            {/* Rules */}
            <div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                <label style={{ margin: 0 }}>Regras de Entrada</label>
                <button type="button" className="btn btn-secondary btn-sm" onClick={addRule}>
                  <Plus size={12} />
                  Adicionar Regra
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {rules.map((rule, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '10px 14px',
                      border: '1px solid var(--border)',
                      borderRadius: 'var(--radius)',
                      background: 'var(--bg-base)',
                    }}
                  >
                    <select
                      className="select"
                      value={rule.actionType}
                      onChange={(e) => updateRule(idx, 'actionType', e.target.value)}
                      style={{ flex: 1 }}
                    >
                      <option value="FOLLOW">Follow</option>
                      <option value="SUBSCRIBE">Subscribe</option>
                      <option value="GIFT_SUB">Gift Sub</option>
                      <option value="BITS">Bits</option>
                      <option value="RAID">Raid</option>
                      <option value="CHAT_MESSAGE">Chat Message</option>
                    </select>
                    <input
                      className="input"
                      type="number"
                      min="1"
                      value={rule.thresholdAmount}
                      onChange={(e) => updateRule(idx, 'thresholdAmount', parseInt(e.target.value) || 1)}
                      style={{ width: 80 }}
                      title="Limite"
                    />
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>x</span>
                    <input
                      className="input"
                      type="number"
                      min="1"
                      value={rule.entries}
                      onChange={(e) => updateRule(idx, 'entries', parseInt(e.target.value) || 1)}
                      style={{ width: 80 }}
                      title="Entries"
                    />
                    {rules.length > 1 && (
                      <button
                        type="button"
                        className="btn btn-secondary btn-icon"
                        onClick={() => removeRule(idx)}
                        title="Remover regra"
                      >
                        <Trash2 size={14} style={{ color: 'var(--error)' }} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => navigate('/events')}
                disabled={submitting}
              >
                Cancelar
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Criando...' : 'Criar Evento'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </Layout>
  );
}
