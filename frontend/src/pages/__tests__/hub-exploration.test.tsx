import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { EventResponse } from '../../api/types';
import { I18nProvider } from '../../i18n/I18nContext';
import Dashboard from '../Dashboard';
import Events from '../Events';
import MyEvents from '../MyEvents';

const api = vi.hoisted(() => ({
  getEvents: vi.fn(),
  getEventStats: vi.fn(),
  getOAuthSession: vi.fn(),
  getMyEventHistory: vi.fn(),
}));

vi.mock('../../api/client', () => api);

const baseEvent: EventResponse = {
  id: 'event-open',
  creatorChannelId: 'channel-1',
  creatorChannelSlug: 'creator',
  creatorChannelDisplayName: 'Creator',
  creatorChannelAvatarUrl: null,
  title: 'Setup completo',
  description: 'Entre pelo comando durante a transmissão.',
  prize: 'Gift card de R$ 100',
  xPostUrl: null,
  entryCommand: '!participar',
  status: 'OPEN',
  finalizedParticipantCount: 0,
  finalizedPoolHash: null,
  startsAt: null,
  endsAt: '2026-07-15T20:00:00Z',
  createdAt: '2026-07-14T12:00:00Z',
  updatedAt: '2026-07-14T12:00:00Z',
  openedAt: '2026-07-14T12:00:00Z',
  finalizationCutoffAt: null,
  closedAt: null,
  completedAt: null,
  enabledActionTypes: ['chat'],
};

function event(overrides: Partial<EventResponse>): EventResponse {
  return { ...baseEvent, ...overrides };
}

function renderPage(page: ReactNode) {
  return render(
    <I18nProvider>
      <MemoryRouter>{page}</MemoryRouter>
    </I18nProvider>,
  );
}

describe('páginas do hub de giveaways', () => {
  beforeEach(() => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
    api.getEvents.mockReset();
    api.getEventStats.mockReset();
    api.getOAuthSession.mockReset();
    api.getMyEventHistory.mockReset();
  });

  it('apresenta no dashboard somente o fluxo por comando e a telemetria do pool', async () => {
    api.getEvents.mockResolvedValue([baseEvent]);
    api.getOAuthSession.mockResolvedValue({ connected: false });
    api.getEventStats.mockResolvedValue({
      eventId: baseEvent.id,
      status: 'OPEN',
      participantCount: 12,
      finalizedParticipantCount: 0,
      captureActive: true,
      captureHealth: 'HEALTHY',
      lastPolledAt: '2026-07-14T12:01:00Z',
      lastSuccessfulPollAt: '2026-07-14T12:01:00Z',
      lastErrorCode: null,
      canFinalize: true,
      canDraw: false,
      openedAt: baseEvent.openedAt,
      closedAt: null,
      completedAt: null,
    });

    renderPage(<Dashboard />);

    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('!participar')).toBeInTheDocument();
    expect(screen.queryByText(/votos|subs|interesse manual/i)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /conectar como criador/i })).toHaveAttribute('href', '/login');
  });

  it('filtra a exploração pública e não publica rascunhos', async () => {
    api.getEvents.mockResolvedValue([
      baseEvent,
      event({ id: 'event-closed', title: 'Pool fechado', status: 'CLOSED', finalizedParticipantCount: 18 }),
      event({ id: 'event-done', title: 'Sorteio concluído', status: 'COMPLETED', finalizedParticipantCount: 22 }),
      event({ id: 'event-draft', title: 'Rascunho privado', status: 'DRAFT' }),
    ]);

    renderPage(<Events />);

    expect(await screen.findByText('Setup completo')).toBeInTheDocument();
    expect(screen.queryByText('Rascunho privado')).not.toBeInTheDocument();

    const filterGroup = screen.getByRole('group', { name: 'Filtrar giveaways por status' });
    expect(filterGroup).toHaveAttribute('tabindex', '0');
    expect(filterGroup).toHaveAttribute('aria-describedby', 'events-filter-scroll-hint');
    expect(screen.getByText(/Deslize ou use Tab para ver todos os filtros/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /sorteados 1/i }));

    expect(screen.getByText('Sorteio concluído')).toBeInTheDocument();
    expect(screen.queryByText('Setup completo')).not.toBeInTheDocument();
  });

  it('permite tentar novamente quando o dashboard falha antes de receber dados', async () => {
    api.getEvents
      .mockRejectedValueOnce(new Error('Hub indisponível'))
      .mockResolvedValueOnce([baseEvent]);
    api.getOAuthSession.mockResolvedValue({ connected: false });
    api.getEventStats.mockResolvedValue({
      eventId: baseEvent.id,
      status: 'OPEN',
      participantCount: 12,
      finalizedParticipantCount: 0,
      captureActive: true,
      captureHealth: 'HEALTHY',
      lastPolledAt: '2026-07-14T12:01:00Z',
      lastSuccessfulPollAt: '2026-07-14T12:01:00Z',
      lastErrorCode: null,
      canFinalize: true,
      canDraw: false,
      openedAt: baseEvent.openedAt,
      finalizationCutoffAt: null,
      closedAt: null,
      completedAt: null,
    });

    renderPage(<Dashboard />);

    fireEvent.click(await screen.findByRole('button', { name: 'Tentar novamente' }));

    expect(await screen.findByText('Setup completo')).toBeInTheDocument();
    expect(api.getEvents).toHaveBeenCalledTimes(2);
  });

  it('agrupa o histórico e leva toda gestão para a rota manage', async () => {
    const draft = event({ id: 'draft-1', title: 'Configurar comando', status: 'DRAFT' });
    const active = event({ id: 'active-1', title: 'Captação ao vivo' });
    const past = event({ id: 'past-1', title: 'Resultado publicado', status: 'COMPLETED', finalizedParticipantCount: 20 });
    api.getOAuthSession.mockResolvedValue({ connected: true });
    api.getMyEventHistory.mockResolvedValue({ drafts: [draft], active: [active], past: [past] });

    renderPage(<MyEvents />);

    await waitFor(() => expect(screen.getByText('Configurar comando')).toBeInTheDocument());
    expect(screen.getByText('Configurar comando').closest('a')).toHaveAttribute('href', '/events/draft-1/manage');
    expect(screen.getByText('Captação ao vivo').closest('a')).toHaveAttribute('href', '/events/active-1/manage');
    expect(screen.getByText('Resultado publicado').closest('a')).toHaveAttribute('href', '/events/past-1/result');
    expect(screen.getByRole('heading', { name: 'Rascunhos' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ativos' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Passados' })).toBeInTheDocument();
  });
});
