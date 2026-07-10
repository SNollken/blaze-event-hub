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
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';
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

type Translate = (key: TranslationKey, params?: Record<string, string | number>) => string;

const numberFormatter = new Intl.NumberFormat('pt-BR');

function formatNumber(value: number | null | undefined) {
  return numberFormatter.format(value ?? 0);
}

function formatLast24h(last24h: EventStatsResponse['last24h'], t: Translate) {
  if (typeof last24h === 'number') {
    return formatNumber(last24h);
  }

  return t('dashboardLast24hBreakdown', {
    votes: formatNumber(last24h?.votes),
    subs: formatNumber(last24h?.subs),
    giftedSubs: formatNumber(last24h?.giftedSubs),
  });
}

function getErrorMessage(error: unknown, t: Translate) {
  return error instanceof Error ? error.message : t('unexpectedError');
}

function isNotFoundError(error: unknown) {
  return error instanceof Error && error.message.startsWith('API 404:');
}

function actionLabel(activeAction: ActionName | null, action: ActionName, label: TranslationKey, t: Translate) {
  return activeAction === action ? t('processing') : t(label);
}

function statusLabel(status: string, t: Translate) {
  const keyByStatus: Record<string, TranslationKey> = {
    OPEN: 'statusOpen',
    CLOSED: 'statusClosed',
    COMPLETED: 'statusCompleted',
    DRAWING: 'statusDrawing',
    DRAFT: 'statusDraft',
    CANCELLED: 'statusCancelled',
  };
  return t(keyByStatus[status] || 'statusDraft');
}

function actionTypeLabel(actionType: string, t: Translate) {
  if (actionType === 'vote') return t('actionVote');
  if (actionType === 'gifted_sub') return t('actionGiftedSub');
  return t('actionSub');
}

export default function EventDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { t } = useI18n();
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
      setErr(t('eventIdMissing'));
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
          setErr(t('winnerLoadError', { error: getErrorMessage(error, t) }));
        }
      }
    } catch (error) {
      setErr(getErrorMessage(error, t));
    } finally {
      setLoading(false);
    }
  }, [id, t]);

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
      setErr(getErrorMessage(error, t));
    } finally {
      setActionLoading(null);
    }
  };

  if (loading && !event) return <div className="empty">{t('eventLoading')}</div>;
  if (err && !event) return <div style={{ padding: 40, color: 'var(--danger)' }}>{err}</div>;
  if (!event) return <div className="empty">{t('eventNotFound')}</div>;

  const statusMap: Record<string, string> = {
    OPEN: 'pill--open',
    CLOSED: 'pill--closed',
    COMPLETED: 'pill--completed',
    DRAWING: 'pill--closed',
    DRAFT: 'pill--draft',
    CANCELLED: 'pill--cancelled',
  };
  const statusClass = statusMap[event.status] || statusMap.DRAFT;

  const activeRules = event.rules?.filter((rule) => rule.isActive) ?? [];
  const mode = event.mode ?? event.rulesMode ?? 'tier';
  const modeLabel = mode === 'tier' ? t('modeTier') : mode === 'cumulative' ? t('modeCumulative') : mode;
  const maxEntries = event.maxEntries ?? event.maxEntriesPerParticipant;
  const maxLabel = maxEntries && maxEntries > 0
    ? `${formatNumber(maxEntries)}${t('perPerson')}`
    : t('unlimited');
  const isActionBusy = actionLoading !== null;
  const canOpen = event.status === 'DRAFT';
  const canClose = event.status === 'OPEN';
  const canCancel = event.status === 'OPEN' || event.status === 'DRAFT';
  const canRecalculate = event.status === 'CLOSED';
  const canDraw = event.status === 'CLOSED' && !winner;
  const statItems = stats ? [
    { label: t('totalVotes'), value: formatNumber(stats.totalVotes) },
    { label: t('totalSubs'), value: formatNumber(stats.totalSubs) },
    { label: t('totalGiftedSubs'), value: formatNumber(stats.totalGiftedSubs) },
    { label: t('participants'), value: formatNumber(stats.participants) },
    { label: t('totalEntries'), value: formatNumber(stats.totalEntries) },
    { label: t('last24h'), value: formatLast24h(stats.last24h, t), compact: true },
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
            {t('eventConfigSummary', {
              mode: modeLabel,
              max: maxLabel,
              rules: t('activeRules', { count: activeRules.length }),
            })}
          </div>
        </div>
        <span className={`pill ${statusClass}`}>{statusLabel(event.status, t)}</span>
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
          {t('editBtn')}
        </button>
        {event.status === 'OPEN' && (
          <>
            <button
              className="btn btn-primary"
              disabled={isActionBusy}
              onClick={() => void runAction('interest', () => expressInterest(event.id))}
            >
              {actionLabel(actionLoading, 'interest', 'participate', t)}
            </button>
            <button
              className="btn btn-secondary"
              disabled={isActionBusy}
              onClick={() => void runAction('withdraw', () => withdrawInterest(event.id))}
            >
              {actionLabel(actionLoading, 'withdraw', 'withdraw', t)}
            </button>
          </>
        )}
        <button
          className="btn btn-success"
          disabled={isActionBusy || !canOpen}
          onClick={() => void runAction('open', () => openEvent(event.id))}
        >
          {actionLabel(actionLoading, 'open', 'open', t)}
        </button>
        <button
          className="btn btn-warning"
          disabled={isActionBusy || !canClose}
          onClick={() => void runAction('close', () => closeEvent(event.id))}
        >
          {actionLabel(actionLoading, 'close', 'closeEvent', t)}
        </button>
        <button
          className="btn btn-danger"
          disabled={isActionBusy || !canCancel}
          onClick={() => void runAction('cancel', () => cancelEvent(event.id))}
        >
          {actionLabel(actionLoading, 'cancel', 'cancel', t)}
        </button>
        <button
          className="btn btn-secondary"
          disabled={isActionBusy || !canRecalculate}
          onClick={() => void runAction('recalculate', () => recalculate(event.id))}
        >
          {actionLabel(actionLoading, 'recalculate', 'recalculate', t)}
        </button>
        <button
          className="btn btn-primary"
          disabled={isActionBusy || !canDraw}
          onClick={() => void runAction('draw', () => executeDraw(event.id), setWinner)}
        >
          {actionLabel(actionLoading, 'draw', 'draw', t)}
        </button>
      </div>

      <section style={{ marginBottom: 32 }}>
        <div className="section-label">{t('metrics')}</div>
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
          <div className="empty">{t('statsUnavailable')}</div>
        )}
      </section>

      {activeRules.length > 0 && (
        <section style={{ marginBottom: 32 }}>
          <div className="section-label">{t('rulesLabel')}</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {activeRules.map((rule) => (
              <div key={rule.id} className="card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--fg2)' }}>
                  {formatNumber(rule.thresholdAmount)} {actionTypeLabel(rule.actionType, t)}
                </span>
                <span style={{ color: 'var(--fg)', fontWeight: 510 }}>
                  {formatNumber(rule.entries)} {rule.entries === 1 ? t('entrySingular') : t('entriesUnit')}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {(event.prizeType || event.prizeDescription) && (
        <section style={{ marginBottom: 32 }}>
          <div className="section-label">{t('prizeSection')}</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {event.prizeType && (
              <div className="card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: 'var(--muted)', fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
                  {t('prizeType')}
                </span>
                <span style={{ color: 'var(--fg)', fontWeight: 510 }}>{event.prizeType}</span>
              </div>
            )}
            {event.prizeDescription && (
              <div className="card">
                <div style={{ fontSize: 11, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 8 }}>
                  {t('prizeDescription')}
                </div>
                <div style={{ fontSize: 13, color: 'var(--fg)', lineHeight: 1.45, whiteSpace: 'pre-wrap' }}>
                  {event.prizeDescription}
                </div>
              </div>
            )}
          </div>
        </section>
      )}

      {winner && (
        <div className="winner-box">
          <div className="section-label">{t('drawWinner')}</div>
          <div className="winner-name">{winner.memberId}</div>
          <div className="winner-meta">
            {t('winnerMetadata', {
              entries: formatNumber(winner.entriesAtDrawTime),
              method: winner.drawMethod,
              seed: winner.drawSeed,
            })}
          </div>
        </div>
      )}

      <section style={{ marginBottom: 32 }}>
        <div className="section-label">
          {t('participantsLabel')} <span className="count">({participants.length})</span>
        </div>
        {participants.length === 0 ? (
          <div className="empty">{t('noInterest')}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {participants.map((participant) => (
              <div key={participant.memberId} className="card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
                <span style={{ fontSize: 13, color: 'var(--fg)' }}>
                  {participant.displayName || participant.blazeUsername || participant.memberId}
                </span>
                <span style={{ fontSize: 12, color: 'var(--muted)', fontFamily: 'var(--font-mono)' }}>
                  {formatNumber(participant.lastCalculatedEntries)} {t('entriesUnit')}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <div className="section-label">
          {t('entriesUnit')} <span className="count">({entries.length})</span>
        </div>
        {entries.length === 0 ? (
          <div className="empty">{t('noEntries')}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {entries.map((entry) => (
              <div key={entry.id} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, marginBottom: 2 }}>
                  <span style={{ fontSize: 13, fontWeight: 510, color: 'var(--fg)' }}>
                    {formatNumber(entry.amount)} {entry.actionType}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 510, color: 'var(--accent-light)' }}>
                    {formatNumber(entry.entriesGranted)} {t('entriesUnit')}
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
