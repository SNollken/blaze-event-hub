import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, FilePenLine, Radio, Trophy } from 'lucide-react';
import { getMyEventHistory, getOAuthSession } from '../api/client';
import type { EventHistoryResponse, EventResponse, EventStatus } from '../api/types';
import { usePolling } from '../components/Toast';
import { useI18n } from '../i18n/I18nContext';

const EMPTY_HISTORY: EventHistoryResponse = { drafts: [], active: [], past: [] };

const STATUS_META: Record<EventStatus, { label: string; className: string }> = {
  DRAFT: { label: 'Rascunho', className: 'status-pill--draft' },
  OPEN: { label: 'Captando', className: 'status-pill--open' },
  FINALIZING: { label: 'Finalizando entradas', className: 'status-pill--finalizing' },
  CLOSED: { label: 'Finalizado', className: 'status-pill--closed' },
  COMPLETED: { label: 'Sorteado', className: 'status-pill--completed' },
  CANCELLED: { label: 'Cancelado', className: 'status-pill--cancelled' },
};

function lifecycleStep(status: EventStatus) {
  if (status === 'DRAFT') return 0;
  if (status === 'OPEN' || status === 'FINALIZING') return 1;
  if (status === 'CLOSED') return 2;
  return 3;
}

function actionLabel(status: EventStatus) {
  if (status === 'DRAFT') return 'Continuar configuração';
  if (status === 'OPEN') return 'Gerenciar captação';
  if (status === 'FINALIZING') return 'Acompanhar finalização';
  if (status === 'CLOSED') return 'Revisar e sortear';
  if (status === 'COMPLETED') return 'Ver resultado';
  return 'Revisar evento';
}

function EventCard({ event }: { event: EventResponse }) {
  const status = STATUS_META[event.status];
  const currentStep = lifecycleStep(event.status);
  const steps = ['Configurar', 'Captar', 'Finalizar', 'Sortear'];
  const target = event.status === 'COMPLETED'
    ? `/events/${event.id}/result`
    : `/events/${event.id}/manage`;

  return (
    <Link to={target} className={`event-card event-card--manage event-card--${event.status.toLowerCase()}`}>
      <div className="event-card__topline">
        <span className={`status-pill ${status.className}`}>{status.label}</span>
        {event.status === 'OPEN' && (
          <span className="event-card__signal" aria-label="Captação ativa"><span aria-hidden="true" /> ao vivo</span>
        )}
      </div>

      <div className="event-card__body">
        {event.creatorChannelSlug && (
          <span className="event-card__creator">@{event.creatorChannelSlug}</span>
        )}
        <p className="event-card__prize">{event.prize || 'Prêmio a confirmar'}</p>
        <h3 className="event-card__title">{event.title}</h3>
        <p className="event-card__description">
          {event.description || 'Evento sem descrição adicional.'}
        </p>
      </div>

      <ol className="lifecycle-mini" aria-label={`Ciclo de ${event.title}`}>
        {steps.map((step, index) => (
          <li
            key={step}
            className={index < currentStep ? 'is-complete' : index === currentStep ? 'is-current' : undefined}
            aria-current={index === currentStep ? 'step' : undefined}
          >
            <span>{String(index + 1).padStart(2, '0')}</span> {step}
          </li>
        ))}
      </ol>

      <footer className="event-card__footer">
        <span>
          {event.status === 'OPEN' && <><Radio size={15} aria-hidden="true" /> {event.entryCommand || '!participar'}</>}
          {event.status === 'FINALIZING' && <><Radio size={15} aria-hidden="true" /> última sincronização</>}
          {event.status === 'DRAFT' && <><FilePenLine size={15} aria-hidden="true" /> configuração pendente</>}
          {(event.status === 'CLOSED' || event.status === 'COMPLETED') && <><Trophy size={15} aria-hidden="true" /> {event.finalizedParticipantCount} no pool</>}
          {event.status === 'CANCELLED' && 'evento cancelado'}
        </span>
        <span className="event-card__link">{actionLabel(event.status)} <ArrowRight size={15} aria-hidden="true" /></span>
      </footer>
    </Link>
  );
}

interface HistorySectionProps {
  title: string;
  eyebrow: string;
  events: EventResponse[];
  emptyText: string;
}

function HistorySection({ title, eyebrow, events, emptyText }: HistorySectionProps) {
  return (
    <section className="history-section" aria-labelledby={`history-${eyebrow}`}>
      <div className="section-heading">
        <div>
          <p className="section-heading__eyebrow">{eyebrow}</p>
          <h2 id={`history-${eyebrow}`}>{title}</h2>
        </div>
        <span className="section-heading__count">{events.length}</span>
      </div>
      {events.length === 0 ? (
        <div className="empty-state empty-state--compact">
          <p>{emptyText}</p>
        </div>
      ) : (
        <div className="event-grid">
          {events.map((event) => <EventCard key={event.id} event={event} />)}
        </div>
      )}
    </section>
  );
}

export default function MyEvents() {
  const { t } = useI18n();
  const fetchHistory = useCallback(async () => {
    const session = await getOAuthSession();
    if (!session.connected) return { history: EMPTY_HISTORY, requiresLogin: true };
    return { history: await getMyEventHistory(), requiresLogin: false };
  }, []);
  const historyState = usePolling(fetchHistory, 10_000);
  const history = historyState.data?.history || EMPTY_HISTORY;
  const loading = historyState.loading && !historyState.data;
  const requiresLogin = historyState.data?.requiresLogin === true;
  const error = Boolean(historyState.error && !historyState.data);

  const totalEvents = useMemo(
    () => history.drafts.length + history.active.length + history.past.length,
    [history],
  );

  return (
    <div className="page hub-page hub-page--my-events">
      <header className="page-hero page-hero--compact">
        <div className="page-hero__copy">
          <p className="page-eyebrow">Painel do criador</p>
          <h1 className="page-title">{t('myEventsTitle')}</h1>
          <p className="page-subtitle">
            Prepare, abra, finalize e sorteie seus giveaways sem perder o estado do evento.
          </p>
        </div>
        <Link to="/events/create" className="btn btn-primary">Criar giveaway</Link>
      </header>

      {historyState.error && historyState.data && (
        <div className="notice notice--warning" role="status">
          A atualização automática falhou. Seu histórico abaixo pode estar desatualizado.
        </div>
      )}

      {loading && (
        <div className="empty-state" role="status" aria-live="polite">
          <span className="empty-state__signal" aria-hidden="true" />
          <h2>Carregando seus eventos</h2>
          <p>Organizando rascunhos, captações e sorteios.</p>
        </div>
      )}

      {!loading && error && (
        <div className="empty-state empty-state--error" role="alert">
          <h2>Não foi possível carregar seus eventos</h2>
          <p>A sessão ou a conexão com o Hub pode ter expirado.</p>
          <div className="empty-state__actions">
            <button type="button" className="btn btn-secondary" onClick={() => void historyState.reload()}>
              Tentar novamente
            </button>
            <Link to="/login" className="btn btn-ghost">Conectar de novo</Link>
          </div>
        </div>
      )}

      {!loading && !error && requiresLogin && (
        <div className="empty-state">
          <Radio size={30} aria-hidden="true" />
          <h2>Conecte sua conta Blaze</h2>
          <p>A conexão identifica seus eventos e permite gerenciar a captação do chat com segurança.</p>
          <Link to="/login" className="btn btn-primary">Conectar conta</Link>
        </div>
      )}

      {!loading && !error && !requiresLogin && totalEvents === 0 && (
        <div className="empty-state">
          <Trophy size={30} aria-hidden="true" />
          <h2>Seu primeiro giveaway começa aqui</h2>
          <p>Cadastre o prêmio e o comando. Depois, abra a captação quando a transmissão estiver pronta.</p>
          <Link to="/events/create" className="btn btn-primary">Criar primeiro giveaway</Link>
        </div>
      )}

      {!loading && !error && !requiresLogin && totalEvents > 0 && (
        <div className="history-groups">
          <div className="creator-summary" aria-label="Resumo dos seus eventos">
            <span><strong>{totalEvents}</strong> no total</span>
            <span><strong>{history.active.length}</strong> em andamento</span>
            <span><strong>{history.drafts.length}</strong> para configurar</span>
          </div>
          <HistorySection
            eyebrow="preparação"
            title="Rascunhos"
            events={history.drafts}
            emptyText="Nenhum rascunho aguardando configuração."
          />
          <HistorySection
            eyebrow="operação"
            title="Ativos"
            events={history.active}
            emptyText="Nenhum evento captando ou aguardando sorteio."
          />
          <HistorySection
            eyebrow="arquivo"
            title="Passados"
            events={history.past}
            emptyText="Seus sorteios concluídos e eventos cancelados aparecerão aqui."
          />
        </div>
      )}
    </div>
  );
}
