import { useMemo } from 'react';
import { useI18n } from '../i18n/I18nContext';

interface Tier {
  threshold: number;
  entries: number;
}

interface TierPreviewProps {
  tiers: Tier[];
  mode: 'REPLACE' | 'ACCUMULATE';
}

export function TierPreview({ tiers, mode }: TierPreviewProps) {
  const { t } = useI18n();

  const sortedTiers = useMemo(() => {
    return [...tiers].sort((a, b) => a.threshold - b.threshold);
  }, [tiers]);

  const examples = useMemo(() => {
    if (sortedTiers.length === 0) return [];
    
    // Create some example test cases slightly above and below thresholds
    const maxThreshold = sortedTiers[sortedTiers.length - 1].threshold;
    const testCases = new Set<number>();
    
    sortedTiers.forEach(t => {
      if (t.threshold > 1) testCases.add(t.threshold - 1);
      testCases.add(t.threshold);
      testCases.add(t.threshold + 1);
    });
    testCases.add(Math.ceil(maxThreshold * 1.5));
    
    return Array.from(testCases).sort((a, b) => a - b).map(count => {
      let earned = 0;
      let matched = false;
      
      if (mode === 'REPLACE') {
        // REPLACE mode: find the highest threshold that the user meets
        for (let i = sortedTiers.length - 1; i >= 0; i--) {
          if (count >= sortedTiers[i].threshold) {
            earned = sortedTiers[i].entries;
            matched = true;
            break;
          }
        }
      } else {
        // ACCUMULATE mode: sum all thresholds the user meets
        sortedTiers.forEach(t => {
          if (count >= t.threshold) {
            earned += t.entries;
            matched = true;
          }
        });
      }
      
      return { count, earned, matched };
    });
  }, [sortedTiers, mode]);

  if (tiers.length === 0) return null;

  return (
    <div className="tier-preview">
      <h3 className="tier-preview__title">{t('tierPreview') || 'Pré-visualização de Tiers'}</h3>
      <p className="tier-preview__desc">
        {mode === 'REPLACE' 
          ? (t('tierModeReplaceDescription') || 'Limites maiores substituem os menores')
          : (t('tierModeAccumulateDescription') || 'Entradas de todos os tiers são somadas')}
      </p>
      
      <table className="tier-preview__table" style={{ width: '100%', marginTop: '1rem', borderCollapse: 'collapse', textAlign: 'left' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid #ccc' }}>
            <th style={{ padding: '0.5rem' }}>Ações (ex: subs)</th>
            <th style={{ padding: '0.5rem' }}>Entradas Ganhas</th>
          </tr>
        </thead>
        <tbody>
          {examples.map(({ count, earned, matched }) => (
            <tr key={count} style={{ borderBottom: '1px solid #eee', opacity: matched ? 1 : 0.6 }}>
              <td style={{ padding: '0.5rem' }}>{count}</td>
              <td style={{ padding: '0.5rem' }}><strong>{earned}</strong>x</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
