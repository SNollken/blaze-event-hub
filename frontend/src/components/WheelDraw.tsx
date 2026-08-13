import { useCallback, useEffect, useRef, useState } from 'react';
import { addToast } from './Toast';
import { t } from '../i18n';
import { drawGiveaway, getGiveawayEntries, getGiveawayResults } from '../api/client';
import { GiveawayEntry } from '../api/types';

/** Above this many participants the wheel is unreadable; offer quick draw instead. */
export const MAX_WHEEL_SEGMENTS = 32;
const SPIN_TURNS = 6;
const SPIN_DURATION_MS = 4200;

/** Angle (deg) of each segment for `n` entries. */
export function segmentAngle(n: number): number {
  return 360 / n;
}

/**
 * Absolute clockwise rotation (deg) that brings segment `winnerIndex` under the
 * pointer at 12 o'clock. Segment i spans [i*angle, (i+1)*angle) from the top.
 * `jitter` in [0,1) picks the landing point inside the segment (0.5 = center),
 * so the wheel does not always stop dead-center (feels organic).
 */
export function targetRotation(winnerIndex: number, n: number, turns: number, jitter: number): number {
  const angle = segmentAngle(n);
  const landingPoint = winnerIndex * angle + jitter * angle;
  return turns * 360 + (360 - landingPoint);
}

/** Truncate long names so segments stay readable. */
export function segmentLabel(name: string, max = 16): string {
  return name.length > max ? `${name.slice(0, max - 1)}…` : name;
}

type WheelPhase = 'loading' | 'ready' | 'spinning' | 'done' | 'too-many' | 'empty' | 'error';

interface WheelDrawProps {
  giveawayId: string;
  /** Called once after a successful draw so the parent can refresh lists. */
  onDrawn: () => void;
}

const CX = 200;
const CY = 200;
const R = 188;

/** Point on the circle for angle measured clockwise from 12 o'clock. */
function pointAt(angleDeg: number, radius = R): { x: number; y: number } {
  const rad = (angleDeg * Math.PI) / 180;
  return { x: CX + radius * Math.sin(rad), y: CY - radius * Math.cos(rad) };
}

function segmentPath(index: number, n: number): string {
  const angle = segmentAngle(n);
  const start = pointAt(index * angle);
  const end = pointAt((index + 1) * angle);
  const largeArc = angle > 180 ? 1 : 0;
  return `M ${CX} ${CY} L ${start.x.toFixed(2)} ${start.y.toFixed(2)} A ${R} ${R} 0 ${largeArc} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)} Z`;
}

const CONFETTI_COLORS = ['#FF6B4A', '#4DB6AC', '#E8A04A', '#E85A3D', '#7FBFB8'];

export function WheelDraw({ giveawayId, onDrawn }: WheelDrawProps) {
  const [entries, setEntries] = useState<GiveawayEntry[]>([]);
  const [phase, setPhase] = useState<WheelPhase>('loading');
  const [rotation, setRotation] = useState(0);
  const [winner, setWinner] = useState<GiveawayEntry | null>(null);
  const drawnRef = useRef(false);

  const loadEntries = useCallback(async () => {
    setPhase('loading');
    try {
      const list = await getGiveawayEntries(giveawayId);
      setEntries(list);
      if (list.length === 0) setPhase('empty');
      else if (list.length > MAX_WHEEL_SEGMENTS) setPhase('too-many');
      else setPhase('ready');
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('giveaways.actionError'));
      setPhase('error');
    }
  }, [giveawayId]);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  const finishDraw = useCallback(
    (result: GiveawayEntry, finalRotation: number, animate: boolean) => {
      setWinner(result);
      setRotation(finalRotation);
      setPhase('done');
      if (!drawnRef.current) {
        drawnRef.current = true;
        onDrawn();
      }
      void animate; // rotation transition is CSS-driven; flag kept for clarity/tests
    },
    [onDrawn],
  );

  const spin = async () => {
    if (phase !== 'ready' || entries.length === 0) return;
    setPhase('spinning');
    try {
      await drawGiveaway(giveawayId, 1);
      const results = await getGiveawayResults(giveawayId);
      const winnerResult = results.winners[0];
      const index = Math.max(
        0,
        entries.findIndex((entry) => entry.id === winnerResult.entryId),
      );
      const jitter = 0.2 + Math.random() * 0.6;
      const target = targetRotation(index, entries.length, SPIN_TURNS, jitter);
      const winnerEntry = entries[index];
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (reducedMotion) {
        finishDraw(winnerEntry, target, false);
      } else {
        // Set target; CSS transition animates, onTransitionEnd reveals winner.
        setWinner(winnerEntry);
        setRotation(target);
      }
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('giveaways.actionError'));
      setPhase('ready');
    }
  };

  const quickDraw = async () => {
    setPhase('spinning');
    try {
      await drawGiveaway(giveawayId, 1);
      const results = await getGiveawayResults(giveawayId);
      const winnerResult = results.winners[0];
      const entry =
        entries.find((candidate) => candidate.id === winnerResult.entryId) ||
        ({ id: winnerResult.entryId, giveawayId, participantName: winnerResult.participantName, enteredAt: winnerResult.enteredAt, selected: true, eligible: true } as GiveawayEntry);
      finishDraw(entry, rotation, false);
    } catch (error) {
      addToast('error', error instanceof Error ? error.message : t('giveaways.actionError'));
      setPhase(entries.length > MAX_WHEEL_SEGMENTS ? 'too-many' : 'ready');
    }
  };

  if (phase === 'loading') {
    return <div className="skeleton-list" aria-label={t('common.loading')} />;
  }

  if (phase === 'error') {
    return (
      <div className="empty-state p-6">
        <span>{t('common.unknownError')}</span>
        <button className="btn btn-secondary btn-sm mt-2" onClick={loadEntries}>{t('error.retry')}</button>
      </div>
    );
  }

  if (phase === 'empty') {
    return <div className="empty-state p-6">{t('giveaways.roletaEmpty')}</div>;
  }

  if (phase === 'too-many') {
    return (
      <div className="empty-state p-6">
        <span>{t('giveaways.roletaTooMany')}</span>
        <button className="btn btn-accent btn-sm mt-2" onClick={quickDraw}>
          {t('giveaways.quickDraw')}
        </button>
      </div>
    );
  }

  const n = entries.length;
  const spinning = phase === 'spinning' && winner !== null;
  const fontSize = n <= 8 ? 15 : n <= 16 ? 12 : 10;

  return (
    <div className="flex flex-col items-center gap-md relative">
      <div className="relative" style={{ width: 320, height: 320 }}>
        <svg viewBox="0 0 400 400" width={320} height={320} role="img" aria-label={t('giveaways.roleta')}>
          {/* pointer */}
          <polygon points="200,30 187,4 213,4" fill="var(--color-text-primary)" />
          <g
            style={{
              transform: `rotate(${rotation}deg)`,
              transformOrigin: '200px 200px',
              transition: spinning ? `transform ${SPIN_DURATION_MS}ms cubic-bezier(0.12, 0.8, 0.18, 1)` : 'none',
            }}
            onTransitionEnd={() => {
              if (phase === 'spinning' && winner) finishDraw(winner, rotation, true);
            }}
          >
            {n === 1 ? (
              <circle cx={CX} cy={CY} r={R} fill="var(--color-primary)" stroke="var(--color-bg-base)" strokeWidth={2} />
            ) : (
              entries.map((entry, index) => (
                <path
                  key={entry.id}
                  d={segmentPath(index, n)}
                  fill={index % 2 === 0 ? 'var(--color-primary)' : 'var(--color-bg-elevated)'}
                  stroke="var(--color-bg-base)"
                  strokeWidth={2}
                />
              ))
            )}
            {entries.map((entry, index) => {
              const bisector = index * segmentAngle(n) + segmentAngle(n) / 2;
              return (
                <g key={`label-${entry.id}`} transform={`rotate(${bisector} ${CX} ${CY})`}>
                  <text
                    x={CX}
                    y={CY - R * 0.62}
                    textAnchor="middle"
                    fontSize={fontSize}
                    fontWeight={600}
                    fill={index % 2 === 0 ? 'var(--color-text-inverse)' : 'var(--color-text-primary)'}
                  >
                    {segmentLabel(entry.participantName)}
                  </text>
                </g>
              );
            })}
            <circle cx={CX} cy={CY} r={26} fill="var(--color-bg-card)" stroke="var(--color-primary)" strokeWidth={3} />
          </g>
        </svg>
        {phase === 'done' && winner && (
          <div aria-hidden="true" className="absolute inset-0 pointer-events-none overflow-visible">
            {Array.from({ length: 24 }).map((_, i) => (
              <span
                key={i}
                className="wheel-confetti absolute rounded-sm"
                style={{
                  left: `${(i * 41) % 100}%`,
                  width: 6 + (i % 3) * 2,
                  height: 6 + ((i + 1) % 3) * 2,
                  background: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
                  animationDelay: `${(i % 8) * 60}ms`,
                }}
              />
            ))}
          </div>
        )}
      </div>

      <div aria-live="assertive" className="text-center">
        {phase === 'done' && winner && (
          <p className="text-lg font-bold text-text-primary">
            {t('giveaways.winner')}: <span className="text-primary">{winner.participantName}</span>
          </p>
        )}
      </div>

      {phase !== 'done' && (
        <button className="btn btn-primary" onClick={spin} disabled={phase !== 'ready'}>
          {phase === 'spinning' ? t('giveaways.spinning') : t('giveaways.spin')}
        </button>
      )}
    </div>
  );
}
