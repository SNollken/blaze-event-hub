import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { BlazeEventsStatusResponse, StatusResponse } from '../api/types';
import LiveEvents from '../pages/LiveEvents';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockGetEventsStatus = vi.hoisted(() => vi.fn());
const mockStartEvents = vi.hoisted(() => vi.fn());
const mockStopEvents = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getStatus: mockGetStatus,
    getEventsStatus: mockGetEventsStatus,
    startEvents: mockStartEvents,
    stopEvents: mockStopEvents,
  };
});

vi.mock('../components/Toast', () => ({
  addToast: mockAddToast,
  ToastContainer: () => null,
}));

function status(overrides: Partial<StatusResponse> = {}): StatusResponse {
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

const stoppedEvents: BlazeEventsStatusResponse = {
  runnerRunning: false,
  clientRunning: false,
  sessionId: null,
  lastMessageType: null,
  startedAt: null,
  messagesSeen: 0,
  acceptedEvents: 0,
  rejectedEvents: 0,
};

const runningEvents: BlazeEventsStatusResponse = {
  runnerRunning: true,
  clientRunning: true,
  sessionId: 'sess-1',
  lastMessageType: 'channel.follow',
  startedAt: '2026-08-26T00:00:00Z',
  messagesSeen: 42,
  acceptedEvents: 7,
  rejectedEvents: 2,
};

async function renderLiveEvents() {
  let utils!: ReturnType<typeof render>;
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={['/events']}>
        <LiveEvents />
      </MemoryRouter>,
    );
  });
  return utils;
}

describe('LiveEvents', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status());
    mockGetEventsStatus.mockReset().mockResolvedValue(stoppedEvents);
    mockStartEvents.mockReset().mockResolvedValue(runningEvents);
    mockStopEvents.mockReset().mockResolvedValue(stoppedEvents);
    mockAddToast.mockReset();
  });

  it('stopped: Start button visible, empty log', async () => {
    await renderLiveEvents();

    expect(await screen.findByRole('button', { name: 'Iniciar eventos' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Parar eventos' })).not.toBeInTheDocument();
    expect(screen.getByText('Nenhum log ainda.')).toBeInTheDocument();
    expect(screen.getByText('0 entradas')).toBeInTheDocument();
  });

  it('running: Stop button visible', async () => {
    mockGetEventsStatus.mockResolvedValue(runningEvents);

    await renderLiveEvents();

    expect(await screen.findByRole('button', { name: 'Parar eventos' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Iniciar eventos' })).not.toBeInTheDocument();
  });

  it('start success: calls startEvents once, appends success line to the log and toasts', async () => {
    await renderLiveEvents();

    fireEvent.click(await screen.findByRole('button', { name: 'Iniciar eventos' }));

    await waitFor(() => expect(mockStartEvents).toHaveBeenCalledTimes(1));

    expect(await screen.findByText('Events Socket iniciado')).toBeInTheDocument();
    expect(screen.getByText('1 entradas')).toBeInTheDocument();

    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('success', 'Events Socket iniciado'),
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Iniciar eventos' })).toBeEnabled(),
    );
  });

  it('start failure: error toast with the message and log line with the error prefix', async () => {
    mockStartEvents.mockRejectedValue(new Error('blaze fora'));

    await renderLiveEvents();

    fireEvent.click(await screen.findByRole('button', { name: 'Iniciar eventos' }));

    await waitFor(() => expect(mockStartEvents).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('error', 'blaze fora'));

    expect(await screen.findByText('ERRO: blaze fora')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Iniciar eventos' })).toBeEnabled(),
    );
  });

  it('stop success: calls stopEvents, appends stop line to the log and toasts', async () => {
    mockGetEventsStatus.mockResolvedValue(runningEvents);

    await renderLiveEvents();

    fireEvent.click(await screen.findByRole('button', { name: 'Parar eventos' }));

    await waitFor(() => expect(mockStopEvents).toHaveBeenCalledTimes(1));

    expect(await screen.findByText('Events Socket parado')).toBeInTheDocument();

    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('success', 'Events Socket parado'),
    );
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Parar eventos' })).toBeEnabled(),
    );
  });

  it('shows the error banner when getEventsStatus rejects', async () => {
    mockGetEventsStatus.mockRejectedValue(new Error('status fora'));

    await renderLiveEvents();

    expect(await screen.findByText('status fora')).toBeInTheDocument();
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('error', 'status fora'));
  });
});
