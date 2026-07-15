import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nContext';
import Dashboard from '../pages/Dashboard';
import Login from '../pages/Login';

const api = vi.hoisted(() => ({
  getEventStats: vi.fn(),
  getEvents: vi.fn(),
  getMe: vi.fn(),
  getOAuthSession: vi.fn(),
  startOAuth: vi.fn(),
}));

vi.mock('../api/client', () => api);

function renderPage(page: React.ReactNode, path = '/') {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[path]}>{page}</MemoryRouter>
    </I18nProvider>,
  );
}

function dashboardEvent(index: number) {
  return {
    id: `event-${index}`,
    creatorChannelId: 'channel-1',
    creatorChannelSlug: 'sofia',
    creatorChannelDisplayName: 'Sofia',
    creatorChannelAvatarUrl: null,
    title: `Giveaway ${index}`,
    description: 'Creator supplied description.',
    prize: 'Creator supplied prize',
    entryCommand: '!join',
    status: 'OPEN',
    finalizedParticipantCount: 0,
    finalizedPoolHash: null,
    startsAt: null,
    endsAt: null,
    createdAt: '2026-07-14T12:00:00Z',
    updatedAt: '2026-07-14T12:00:00Z',
    openedAt: '2026-07-14T12:00:00Z',
    finalizationCutoffAt: null,
    closedAt: null,
    completedAt: null,
  };
}

function dashboardStats() {
  return {
    eventId: 'event-0',
    status: 'OPEN',
    participantCount: 1_234,
    finalizedParticipantCount: 0,
    captureActive: true,
    captureHealth: 'HEALTHY',
    lastPolledAt: '2026-07-14T12:00:00Z',
    lastSuccessfulPollAt: '2026-07-14T12:00:00Z',
    lastErrorCode: null,
    canFinalize: true,
    canDraw: false,
    openedAt: '2026-07-14T12:00:00Z',
    finalizationCutoffAt: null,
    closedAt: null,
    completedAt: null,
  };
}

describe('idioma e simplificacao das telas de entrada', () => {
  beforeEach(() => {
    api.getEventStats.mockReset();
    api.getEvents.mockReset();
    api.getMe.mockReset();
    api.getOAuthSession.mockReset();
    api.startOAuth.mockReset();
    localStorage.clear();
  });

  it('usa ingles por padrao no dashboard e remove o card de fluxo', async () => {
    api.getEvents.mockResolvedValue([]);
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Dashboard />);

    expect(await screen.findByText('No giveaways are accepting entries right now')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Explore giveaways' })).toHaveAttribute('href', '/events');
    expect(screen.queryByText('Event flow')).not.toBeInTheDocument();
    expect(screen.queryByText('Fluxo do evento')).not.toBeInTheDocument();
  });

  it('respeita pt-BR salvo no dashboard', async () => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
    api.getEvents.mockResolvedValue([]);
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Dashboard />);

    expect(await screen.findByText('Nenhum giveaway captando agora')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Explorar giveaways' })).toHaveAttribute('href', '/events');
  });

  it('formata contadores grandes do dashboard em ingles', async () => {
    api.getEvents.mockResolvedValue(Array.from({ length: 1_234 }, (_, index) => dashboardEvent(index)));
    api.getEventStats.mockResolvedValue(dashboardStats());
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Dashboard />);

    expect(await screen.findByText('1,234 giveaways')).toBeInTheDocument();
    expect(await screen.findByText('1,234')).toBeInTheDocument();
  });

  it('formata contadores grandes do dashboard em pt-BR', async () => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
    api.getEvents.mockResolvedValue(Array.from({ length: 1_234 }, (_, index) => dashboardEvent(index)));
    api.getEventStats.mockResolvedValue(dashboardStats());
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Dashboard />);

    expect(await screen.findByText('1.234 eventos')).toBeInTheDocument();
    expect(await screen.findByText('1.234')).toBeInTheDocument();
  });

  it('usa ingles no login sem repetir a marca dentro do card', async () => {
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Login />, '/login');

    expect(await screen.findByRole('heading', { name: 'Connect your Blaze account' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Connect with Blaze' })).toBeInTheDocument();
    expect(screen.queryByText('BEH')).not.toBeInTheDocument();
    expect(screen.queryByText(/^Blaze Event Hub$/)).not.toBeInTheDocument();
  });

  it('mostra no app uma falha segura recebida pelo callback OAuth', async () => {
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Login />, '/login?oauth=error');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "We couldn't connect your Blaze account. Please try again.",
    );
  });

  it('traduz o login para pt-BR quando essa preferencia esta salva', async () => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
    api.getOAuthSession.mockResolvedValue({ connected: false });

    renderPage(<Login />, '/login');

    expect(await screen.findByRole('heading', { name: 'Conecte sua conta Blaze' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Conectar com Blaze' })).toBeInTheDocument();
  });
});
