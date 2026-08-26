import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  Giveaway,
  GiveawayResultsResponse,
  GiveawayStatsResponse,
  StatusResponse,
} from '../api/types';
import Giveaways from '../pages/Giveaways';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockGetGiveaways = vi.hoisted(() => vi.fn());
const mockGetGiveawayStats = vi.hoisted(() => vi.fn());
const mockCreateGiveaway = vi.hoisted(() => vi.fn());
const mockOpenGiveaway = vi.hoisted(() => vi.fn());
const mockCloseGiveaway = vi.hoisted(() => vi.fn());
const mockDrawGiveaway = vi.hoisted(() => vi.fn());
const mockEnterGiveaway = vi.hoisted(() => vi.fn());
const mockGetGiveawayResults = vi.hoisted(() => vi.fn());
const mockGetGiveawayEntries = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getStatus: mockGetStatus,
    getGiveaways: mockGetGiveaways,
    getGiveawayStats: mockGetGiveawayStats,
    createGiveaway: mockCreateGiveaway,
    openGiveaway: mockOpenGiveaway,
    closeGiveaway: mockCloseGiveaway,
    drawGiveaway: mockDrawGiveaway,
    enterGiveaway: mockEnterGiveaway,
    getGiveawayResults: mockGetGiveawayResults,
    getGiveawayEntries: mockGetGiveawayEntries,
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

function stats(overrides: Partial<GiveawayStatsResponse> = {}): GiveawayStatsResponse {
  return {
    totalGiveaways: 0,
    draftCount: 0,
    openCount: 0,
    closedCount: 0,
    completedCount: 0,
    cancelledCount: 0,
    totalEntries: 0,
    entriesPerGiveaway: {},
    ...overrides,
  };
}

function results(overrides: Partial<GiveawayResultsResponse> = {}): GiveawayResultsResponse {
  return {
    giveawayId: 'g1',
    title: 'Sorteio',
    status: 'COMPLETED',
    totalEntries: 0,
    winnerCount: 0,
    winners: [],
    drawnAt: null,
    ...overrides,
  };
}

async function renderGiveaways() {
  let utils!: ReturnType<typeof render>;
  // act async flusha os fetches iniciais do usePolling (mocks resolvem em microtask)
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={['/giveaways']}>
        <Giveaways />
      </MemoryRouter>,
    );
  });
  return utils;
}

describe('Giveaways', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status({}));
    mockGetGiveaways.mockReset().mockResolvedValue([]);
    mockGetGiveawayStats.mockReset().mockResolvedValue(stats());
    mockCreateGiveaway.mockReset().mockResolvedValue(giveaway());
    mockOpenGiveaway.mockReset().mockResolvedValue(giveaway());
    mockCloseGiveaway.mockReset().mockResolvedValue(giveaway());
    mockDrawGiveaway.mockReset().mockResolvedValue(giveaway());
    mockEnterGiveaway.mockReset().mockResolvedValue({
      id: 'e1',
      giveawayId: 'g1',
      participantName: 'alice',
      enteredAt: '2026-08-13T00:00:00Z',
      selected: false,
      eligible: true,
    });
    mockGetGiveawayResults.mockReset().mockResolvedValue(results());
    mockGetGiveawayEntries.mockReset().mockResolvedValue([]);
    mockAddToast.mockReset();
  });

  it('estado vazio: mensagem na tabela e estatisticas zeradas', async () => {
    await renderGiveaways();

    expect(await screen.findByText('Nenhum sorteio criado.')).toBeInTheDocument();
    // tres cartoes de estatistica (Sorteios Abertos / Participantes / Finalizados)
    expect(screen.getAllByText('0')).toHaveLength(3);
  });

  it('acoes por status nas linhas e cartoes com os valores do stats', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g1', title: 'Sorteio A', status: 'DRAFT' }),
      giveaway({ id: 'g2', title: 'Sorteio B', status: 'OPEN' }),
      giveaway({ id: 'g3', title: 'Sorteio C', status: 'CLOSED' }),
    ]);
    mockGetGiveawayStats.mockResolvedValue(
      stats({ totalGiveaways: 3, draftCount: 1, openCount: 1, closedCount: 1, completedCount: 2, totalEntries: 5 }),
    );
    await renderGiveaways();

    // DRAFT -> abrir; OPEN -> fechar; CLOSED -> sortear + roleta
    expect(
      await screen.findByRole('button', { name: 'Abrir sorteio Sorteio A' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fechar sorteio Sorteio B' })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Sortear ganhador de Sorteio C' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Sortear vencedor com roleta Sorteio C' }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Ver' })).toHaveLength(3);

    // cartoes: Sorteios Abertos 1 / Participantes 5 / Finalizados 2
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('abre um sorteio em rascunho', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g1', title: 'Sorteio A', status: 'DRAFT' }),
    ]);
    await renderGiveaways();

    fireEvent.click(await screen.findByRole('button', { name: 'Abrir sorteio Sorteio A' }));

    await waitFor(() => expect(mockOpenGiveaway).toHaveBeenCalledWith('g1'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Sorteio aberto'));
    // reload apos a acao: fetch inicial + reload
    await waitFor(() => expect(mockGetGiveaways).toHaveBeenCalledTimes(2));
  });

  it('sorteia o ganhador de um sorteio fechado', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g3', title: 'Sorteio C', status: 'CLOSED' }),
    ]);
    await renderGiveaways();

    fireEvent.click(
      await screen.findByRole('button', { name: 'Sortear ganhador de Sorteio C' }),
    );

    await waitFor(() => expect(mockDrawGiveaway).toHaveBeenCalledWith('g3', 1));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Sorteio realizado'));
    await waitFor(() => expect(mockGetGiveaways).toHaveBeenCalledTimes(2));
  });

  it('modal de detalhes mostra o titulo e a ganhadora quando ha vencedores', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g4', title: 'Sorteio da Carol', status: 'COMPLETED', entryCount: 1 }),
    ]);
    mockGetGiveawayResults.mockImplementation((id: string) =>
      Promise.resolve(
        results({
          giveawayId: id,
          title: 'Sorteio da Carol',
          winnerCount: id === 'g4' ? 1 : 0,
          winners:
            id === 'g4'
              ? [{ entryId: 'e1', participantName: 'carol', enteredAt: '2026-08-13T00:00:00Z' }]
              : [],
        }),
      ),
    );
    await renderGiveaways();

    fireEvent.click(await screen.findByRole('button', { name: 'Ver' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Sorteio da Carol' })).toBeInTheDocument();
    expect(await within(dialog).findByText('carol')).toBeInTheDocument();
  });

  it('modal de detalhes sem vencedores mostra o estado vazio', async () => {
    mockGetGiveaways.mockResolvedValue([
      giveaway({ id: 'g5', title: 'Sorteio Vazio', status: 'COMPLETED' }),
    ]);
    await renderGiveaways();

    fireEvent.click(await screen.findByRole('button', { name: 'Ver' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Sorteio Vazio' })).toBeInTheDocument();
    expect(
      await within(dialog).findByText('Nenhum ganhador sorteado ainda.'),
    ).toBeInTheDocument();
  });

  it('cria sorteio pelo modal do cabecalho com titulo aparado', async () => {
    await renderGiveaways();

    fireEvent.click(await screen.findByRole('button', { name: 'Novo Sorteio' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Novo Sorteio' })).toBeInTheDocument();

    const createButton = within(dialog).getByRole('button', { name: 'Criar Sorteio' });
    expect(createButton).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText('Nome do Sorteio'), {
      target: { value: '  Roleta Premiada  ' },
    });
    expect(createButton).toBeEnabled();

    fireEvent.click(createButton);

    await waitFor(() =>
      expect(mockCreateGiveaway).toHaveBeenCalledWith({
        title: 'Roleta Premiada',
        description: '',
        maxEntries: 100,
      }),
    );
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('success', 'Sorteio criado'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(mockGetGiveaways).toHaveBeenCalledTimes(2));
  });

  it('erro no polling mostra o banner e o toast de erro', async () => {
    mockGetGiveaways.mockRejectedValue(new Error('falha'));
    await renderGiveaways();

    expect(await screen.findByText('falha')).toBeInTheDocument();
    expect(mockAddToast).toHaveBeenCalledWith('error', 'falha');
  });
});
