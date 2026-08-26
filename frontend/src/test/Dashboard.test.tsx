import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Giveaway, StatusResponse } from '../api/types';
import Dashboard from '../pages/Dashboard';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockGetGiveaways = vi.hoisted(() => vi.fn());
const mockCreateGiveaway = vi.hoisted(() => vi.fn());
const mockEnterGiveaway = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getStatus: mockGetStatus,
    getGiveaways: mockGetGiveaways,
    createGiveaway: mockCreateGiveaway,
    enterGiveaway: mockEnterGiveaway,
  };
});

vi.mock('../components/Toast', () => ({
  addToast: mockAddToast,
  ToastContainer: () => null,
}));

function status(overrides: Partial<StatusResponse>): StatusResponse {
  return {
    appName: 'Blaze Event Hub',
    version: 'test',
    javaVersion: '21',
    blazeOAuthConfigured: false,
    blazeApiConfigured: false,
    socketConfigured: false,
    tokenPresent: false,
    refreshCredentialPresent: false,
    monitoredChannelConfigured: false,
    eventsRunning: false,
    sessionIdPresent: false,
    activeProfilesCount: 0,
    overlaysCount: 0,
    uptimeSeconds: 1,
    oauthConnected: false,
    profilePresent: false,
    connectedAccountDisplayName: '',
    connectedAccountId: '',
    lastProfileSyncAt: null,
    nextRecommendedAction: 'CONNECT_BLAZE',
    ...overrides,
  };
}

function giveaway(overrides: Partial<Giveaway> = {}): Giveaway {
  return {
    id: 'g1',
    title: 'Sorteio',
    description: '',
    status: 'DRAFT',
    entryCount: 0,
    maxEntries: 100,
    createdAt: '2026-08-01T12:00:00Z',
    openedAt: null,
    closedAt: null,
    drawnAt: null,
    winnerIds: [],
    ...overrides,
  };
}

async function renderDashboard() {
  let utils!: ReturnType<typeof render>;
  // act async flusha o fetch inicial do usePolling (mock resolve em microtask)
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={['/']}>
        <Dashboard />
      </MemoryRouter>,
    );
  });
  return utils;
}

describe('Dashboard', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status({}));
    mockGetGiveaways.mockReset().mockResolvedValue([]);
    mockCreateGiveaway.mockReset().mockResolvedValue(giveaway());
    mockEnterGiveaway.mockReset().mockResolvedValue({
      id: 'e1',
      giveawayId: 'g1',
      participantName: 'alice',
      enteredAt: '2026-08-13T00:00:00Z',
      selected: false,
      eligible: true,
    });
    mockAddToast.mockReset();
  });

  it('mostra o skeleton enquanto os sorteios nao carregam', async () => {
    mockGetGiveaways.mockImplementation(() => new Promise(() => {}));
    const { container } = await renderDashboard();
    expect(container.querySelector('.skeleton-list')).not.toBeNull();
  });

  it('estado vazio: hero, sem sorteios abertos e recentes vazios', async () => {
    await renderDashboard();
    expect(
      await screen.findByText('Sorteios e eventos para a sua comunidade'),
    ).toBeInTheDocument();
    expect(screen.getByText('Nenhum sorteio aberto no momento.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Criar o primeiro' })).toBeInTheDocument();
    expect(screen.getByText('Nenhum sorteio criado ainda.')).toBeInTheDocument();
  });

  it('sorteio aberto: campo de nome, participar desabilitado e lista recente', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g1', title: 'Sorteio da Comunidade', status: 'OPEN', entryCount: 7, maxEntries: 42 }),
    ]);
    await renderDashboard();

    const nameInput = await screen.findByLabelText('Seu nome');
    expect(nameInput).toBeInTheDocument();

    const joinButton = screen.getByRole('button', { name: 'Participar' });
    expect(joinButton).toBeDisabled();

    expect(await screen.findByText('7/42 participantes')).toBeInTheDocument();

    // titulo aparece na lista de abertos e na lista de recentes
    expect(screen.getAllByText('Sorteio da Comunidade')).toHaveLength(2);
    expect(screen.getByText('Aberto')).toBeInTheDocument();
  });

  it('participa com o nome aparado e mostra toast de sucesso', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g1', title: 'Sorteio da Comunidade', status: 'OPEN' }),
    ]);
    await renderDashboard();

    fireEvent.change(await screen.findByLabelText('Seu nome'), {
      target: { value: '  alice  ' },
    });
    const joinButton = screen.getByRole('button', { name: 'Participar' });
    expect(joinButton).toBeEnabled();

    fireEvent.click(joinButton);

    await waitFor(() => expect(mockEnterGiveaway).toHaveBeenCalledWith('g1', 'alice'));
    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('success', 'Você está participando!'),
    );
    // reload apos entrar: fetch inicial + reload
    await waitFor(() => expect(mockGetGiveaways).toHaveBeenCalledTimes(2));
  });

  it('cria sorteio pelo modal do hero', async () => {
    await renderDashboard();

    fireEvent.click(screen.getByRole('button', { name: 'Criar sorteio' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Novo sorteio' })).toBeInTheDocument();

    const createButton = within(dialog).getByRole('button', { name: 'Criar sorteio' });
    expect(createButton).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText('Nome do sorteio'), {
      target: { value: 'Roleta' },
    });
    expect(createButton).toBeEnabled();

    fireEvent.click(createButton);

    await waitFor(() =>
      expect(mockCreateGiveaway).toHaveBeenCalledWith({
        title: 'Roleta',
        description: '',
        maxEntries: 100,
      }),
    );
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Sorteio criado'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(mockGetGiveaways).toHaveBeenCalledTimes(2));
  });

  it('erro no polling mostra o banner e o toast de erro', async () => {
    mockGetGiveaways.mockRejectedValue(new Error('api caiu'));
    await renderDashboard();

    expect(await screen.findByText('api caiu')).toBeInTheDocument();
    expect(mockAddToast).toHaveBeenCalledWith('error', 'api caiu');
  });
});
