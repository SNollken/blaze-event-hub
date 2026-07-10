import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  executeDraw,
  getEvent,
  getEventStats,
  getParticipants,
  getWinner,
} from '../api/client';
import type {
  EventResponse,
  EventStatsResponse,
  ParticipantResponse,
  WinnerResponse,
} from '../api/client';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

type DrawPhase = 'idle' | 'rolling' | 'done';
type Translate = (key: TranslationKey, params?: Record<string, string | number>) => string;

type DrawParticipant = {
  memberId: string;
  displayName: string;
  blazeUsername: string;
  entries: number;
};

const ROLL_INTERVAL_MS = 70;
const MINIMUM_ROLL_MS = 1800;

function wait(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function toDrawParticipant(participant: ParticipantResponse): DrawParticipant {
  return {
    memberId: participant.memberId,
    displayName: participant.displayName,
    blazeUsername: participant.blazeUsername,
    entries: participant.lastCalculatedEntries,
  };
}

function participantName(participant: DrawParticipant | undefined, t: Translate) {
  return participant?.displayName || participant?.blazeUsername || t('drawUnknownParticipant');
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

export default function LiveDraw() {
  const { id } = useParams<{ id: string }>();
  const { t } = useI18n();
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [stats, setStats] = useState<EventStatsResponse | null>(null);
  const [participants, setParticipants] = useState<DrawParticipant[]>([]);
  const [winner, setWinner] = useState<WinnerResponse | null>(null);
  const [phase, setPhase] = useState<DrawPhase>('idle');
  const [displayName, setDisplayName] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const spinRef = useRef<ReturnType<typeof window.setInterval> | null>(null);
  const mountedRef = useRef(true);

  const stopRolling = useCallback(() => {
    if (spinRef.current !== null) {
      window.clearInterval(spinRef.current);
      spinRef.current = null;
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      stopRolling();
    };
  }, [stopRolling]);

  useEffect(() => {
    let active = true;
    const eventId = id;

    if (!eventId) {
      setEvent(null);
      setStats(null);
      setParticipants([]);
      setWinner(null);
      setError(t('drawNoEventId'));
      setLoading(false);
      return () => {
        active = false;
      };
    }

    async function loadDraw(eventId: string) {
      setLoading(true);
      setError('');
      setPhase('idle');
      setWinner(null);

      const [eventResult, statsResult, participantsResult] = await Promise.allSettled([
        getEvent(eventId),
        getEventStats(eventId),
        getParticipants(eventId),
      ]);

      if (!active) return;

      if (eventResult.status === 'rejected') {
        setEvent(null);
        setError(t('drawEventLoadError'));
        setLoading(false);
        return;
      }

      const loadedEvent = eventResult.value;
      setEvent(loadedEvent);

      if (statsResult.status === 'fulfilled') {
        setStats(statsResult.value);
      } else {
        setStats(null);
        setError(t('drawStatsLoadError'));
      }

      const loadedParticipants = participantsResult.status === 'fulfilled'
        ? participantsResult.value.map(toDrawParticipant)
        : [];
      setParticipants(loadedParticipants);
      if (participantsResult.status === 'rejected') {
        setError(t('drawParticipantsLoadError'));
      }

      if (loadedEvent.status === 'COMPLETED') {
        try {
          const existingWinner = await getWinner(eventId);
          if (!active) return;
          setWinner(existingWinner);
          setDisplayName(
            participantName(
              loadedParticipants.find((participant) => participant.memberId === existingWinner.memberId),
              t,
            ) || existingWinner.memberId,
          );
          setPhase('done');
        } catch {
          if (active) setError(t('drawFailed'));
        }
      }

      if (active) setLoading(false);
    }

    void loadDraw(eventId);

    return () => {
      active = false;
    };
  }, [id, t]);

  const canDraw = Boolean(id)
    && event?.status === 'CLOSED'
    && participants.length > 0
    && phase !== 'rolling'
    && !winner;

  const runDraw = useCallback(async () => {
    if (!id || !canDraw) return;

    const names = participants.map((participant) => participantName(participant, t));
    setError('');
    setPhase('rolling');
    setDisplayName(names[Math.floor(Math.random() * names.length)] || t('drawReady'));
    spinRef.current = window.setInterval(() => {
      setDisplayName(names[Math.floor(Math.random() * names.length)] || t('drawReady'));
    }, ROLL_INTERVAL_MS);

    try {
      const [drawnWinner] = await Promise.all([
        executeDraw(id),
        wait(MINIMUM_ROLL_MS),
      ]);
      stopRolling();
      if (!mountedRef.current) return;

      const selectedParticipant = participants.find((participant) => participant.memberId === drawnWinner.memberId);
      setWinner(drawnWinner);
      setDisplayName(participantName(selectedParticipant, t) || drawnWinner.memberId);
      setEvent((current) => current ? { ...current, status: 'COMPLETED' } : current);
      setPhase('done');
    } catch (drawError) {
      stopRolling();
      if (!mountedRef.current) return;
      setError(getErrorMessage(drawError, t('drawFailed')));
      setDisplayName('');
      setPhase('idle');
    }
  }, [canDraw, id, participants, phase, stopRolling, t]);

  const sortedPool = useMemo(
    () => [...participants].sort((first, second) => second.entries - first.entries),
    [participants],
  );
  const winnerParticipant = winner
    ? participants.find((participant) => participant.memberId === winner.memberId)
    : undefined;
  const winnerName = winner
    ? participantName(winnerParticipant, t) || winner.memberId
    : '';
  const stageClass = `draw-stage${phase === 'rolling' ? ' rolling' : ''}${phase === 'done' ? ' done' : ''}`;
  const stageName = phase === 'idle'
    ? (loading ? t('appLoading') : t('drawReady'))
    : displayName || t('drawReady');
  const participantCount = stats?.participants ?? participants.length;
  const entryCount = stats?.totalEntries ?? participants.reduce((total, participant) => total + participant.entries, 0);

  return (
    <div className="page" style={{ maxWidth: 760 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap', marginBottom: 8 }}>
        <div>
          <h1 className="page-title" style={{ fontSize: 22 }}>{t('drawTitle')}</h1>
          <p className="page-subtitle" style={{ marginBottom: 0 }}>{t('drawServerDescription')}</p>
        </div>
        {phase === 'rolling' && <span className="pill pill--drawing">{t('drawRolling')}</span>}
        {phase === 'done' && <span className="pill pill--completed">{t('drawWinner')}</span>}
      </div>

      {error && <div className="toast toast-error" style={{ position: 'static', margin: '18px 0' }}>{error}</div>}

      {event && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '18px 0 28px', padding: '12px 14px', background: 'var(--bg-card)', border: '1px solid var(--border-card)', borderRadius: 'var(--r-md)', maxWidth: 640 }}>
          <div style={{ width: 34, height: 34, borderRadius: '50%', background: 'var(--accent-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 600, color: 'var(--accent-light)', flexShrink: 0 }}>
            {event.title?.[0]?.toUpperCase()}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 14, fontWeight: 510, color: 'var(--fg)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{event.title}</div>
            <div style={{ fontSize: 12, color: 'var(--fg2)', fontFamily: 'var(--font-mono)' }}>
              {t('drawPoolMeta', { participants: participantCount, entries: entryCount })}
            </div>
          </div>
        </div>
      )}

      <div className={stageClass} aria-live="polite" aria-busy={phase === 'rolling'}>
        <div className="draw-reel">
          <div className="draw-name">{stageName}</div>
        </div>
        <div className="draw-glow" />
      </div>

      {!winner && (
        <div className="draw-controls">
          <button className="btn btn-primary btn-lg" onClick={() => void runDraw()} disabled={!canDraw}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 3l14 9-14 9V3z" fill="currentColor" stroke="none" /></svg>
            {phase === 'rolling' ? t('drawRolling') : t('drawStart')}
          </button>
          {event?.status === 'COMPLETED' && <span className="form-helper">{t('drawCompleted')}</span>}
          {event && event.status !== 'CLOSED' && event.status !== 'COMPLETED' && <span className="form-helper">{t('drawOnlyClosed')}</span>}
          {!loading && participants.length === 0 && <span className="form-helper form-helper--err">{t('drawNoPoolParticipants')}</span>}
        </div>
      )}

      {winner && (
        <section>
          <div className="section-label" style={{ marginTop: 8 }}>{t('drawWinner')}</div>
          <div className="draw-winner">
            <span className="dw-avatar">{winnerName[0]?.toUpperCase()}</span>
            <div style={{ minWidth: 0 }}>
              <div className="dw-name">{winnerName}</div>
              <div className="dw-entries">{winner.entriesAtDrawTime} {t('entriesUnit')}</div>
            </div>
          </div>

          <div className="draw-seed-box">
            <span className="draw-seed-lbl">{t('drawSeed')}</span>
            <span className="draw-seed-val">{winner.drawSeed}</span>
          </div>

          <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
            <Link to={id ? `/events/${id}` : '/events'} className="btn btn-secondary">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12" /><polyline points="12 19 5 12 12 5" /></svg>
              {t('drawBack')}
            </Link>
          </div>
        </section>
      )}

      <section>
        <div className="section-label" style={{ marginTop: 36 }}>{t('drawParticipantPool')}</div>
        {sortedPool.length === 0 ? (
          <div className="empty">{t('drawNoPoolParticipants')}</div>
        ) : (
          <div className="draw-pool">
            {sortedPool.map((participant) => {
              const name = participantName(participant, t);
              return (
                <div key={participant.memberId} className="draw-pool-item">
                  <span className="dpi-avatar">{name[0]?.toUpperCase()}</span>
                  <span style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{name}</span>
                  <span className="dpi-entries">{participant.entries}</span>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
