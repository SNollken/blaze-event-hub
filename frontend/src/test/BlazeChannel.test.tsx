import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  BlazeSetupStatusResponse,
  OAuthActionResponse,
  OAuthSessionResponse,
  StatusResponse,
} from '../api/types';
import BlazeChannel from '../pages/BlazeChannel';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockGetSetupStatus = vi.hoisted(() => vi.fn());
const mockGetOAuthSession = vi.hoisted(() => vi.fn());
const mockStartOAuth = vi.hoisted(() => vi.fn());
const mockDisconnectOAuth = vi.hoisted(() => vi.fn());
const mockRefreshOAuth = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getStatus: mockGetStatus,
    getSetupStatus: mockGetSetupStatus,
    getOAuthSession: mockGetOAuthSession,
    startOAuth: mockStartOAuth,
    disconnectOAuth: mockDisconnectOAuth,
    refreshOAuth: mockRefreshOAuth,
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

function setupStatus(
  overrides: Partial<BlazeSetupStatusResponse> = {},
): BlazeSetupStatusResponse {
  return {
    appName: 'Blaze Event Hub',
    environment: 'test',
    clientIdConfigured: false,
    clientIdMasked: null,
    clientCredentialConfigured: false,
    redirectUriConfigured: false,
    redirectUri: null,
    requestedScopes: [],
    recommendedScopes: [],
    tokenPresent: false,
    tokenExpiredOrUnknown: false,
    refreshCredentialPresent: false,
    oauthConnected: false,
    profilePresent: false,
    connectedAccountDisplayName: null,
    connectedAccountId: null,
    lastProfileSyncAt: null,
    nextRecommendedAction: null,
    monitoredChannelConfigured: false,
    monitoredChannel: null,
    eventsConfigReady: false,
    oauthStartReady: false,
    checklist: [],
    missingItems: [],
    nextSteps: [],
    docsLinks: [],
    envExample: null,
    ...overrides,
  };
}

function oauthSession(
  overrides: Partial<OAuthSessionResponse> = {},
): OAuthSessionResponse {
  return {
    connected: false,
    tokenPresent: false,
    refreshCredentialPresent: false,
    profilePresent: false,
    profile: null,
    tokenType: null,
    userId: null,
    scopes: [],
    expiresAt: null,
    tokenExpiredOrUnknown: false,
    lastConnectedAt: null,
    lastProfileSyncAt: null,
    nextRecommendedAction: null,
    ...overrides,
  };
}

function connectedOAuth(): OAuthSessionResponse {
  return oauthSession({
    connected: true,
    tokenPresent: true,
    refreshCredentialPresent: true,
    profilePresent: true,
    profile: {
      id: 'u1',
      username: 'streamer',
      displayName: 'Streamer Exemplo',
      avatarUrl: null,
    },
    tokenType: 'Bearer',
    userId: 'u1',
    scopes: ['users.read', 'offline.access'],
    expiresAt: '2026-09-01T00:00:00Z',
    tokenExpiredOrUnknown: false,
    lastConnectedAt: '2026-08-26T00:00:00Z',
    lastProfileSyncAt: '2026-08-26T00:00:00Z',
  });
}

async function renderPage() {
  let utils!: ReturnType<typeof render>;
  // act async flusha os fetches iniciais dos tres usePolling + Sidebar
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={['/blaze']}>
        <BlazeChannel />
      </MemoryRouter>,
    );
  });
  return utils;
}

function oauthAction(overrides: Partial<OAuthActionResponse> = {}): OAuthActionResponse {
  return {
    status: 'refreshed',
    refreshed: true,
    disconnected: false,
    connected: true,
    tokenPresent: true,
    refreshCredentialPresent: true,
    profilePresent: true,
    profile: null,
    expiresAt: null,
    nextRecommendedAction: 'READY_FOR_EVENTS',
    message: 'ok',
    ...overrides,
  };
}

describe('BlazeChannel', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status());
    mockGetSetupStatus.mockReset().mockResolvedValue(setupStatus());
    mockGetOAuthSession.mockReset().mockResolvedValue(oauthSession());
    mockStartOAuth
      .mockReset()
      .mockResolvedValue({ authorizationUrl: 'https://id.blaze.test/x', scopes: ['users.read', 'offline.access'] });
    mockDisconnectOAuth
      .mockReset()
      .mockResolvedValue(oauthAction({ status: 'disconnected', refreshed: false, disconnected: true, connected: false }));
    mockRefreshOAuth.mockReset().mockResolvedValue(oauthAction());
    mockAddToast.mockReset();
  });

  it('disables connect when OAuth is not ready and shows absent token / no account', async () => {
    await renderPage();

    const connect = await screen.findByRole('button', {
      name: 'Conectar com a Blaze',
    });
    expect(connect).toBeDisabled();
    expect(screen.getByText('Token: Ausente')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma conta conectada.')).toBeInTheDocument();
  });

  it('enables the connect button when oauthStartReady is true', async () => {
    mockGetSetupStatus.mockResolvedValue(setupStatus({ oauthStartReady: true }));
    await renderPage();

    const connect = await screen.findByRole('button', {
      name: 'Conectar com a Blaze',
    });
    expect(connect).toBeEnabled();
  });

  it('opens the authorization URL in a new tab and toasts success on connect', async () => {
    mockGetSetupStatus.mockResolvedValue(setupStatus({ oauthStartReady: true }));
    mockStartOAuth.mockResolvedValue({
      authorizationUrl: 'https://id.blaze.test/x',
      scopes: ['users.read', 'offline.access'],
    });
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);
    await renderPage();

    fireEvent.click(
      await screen.findByRole('button', { name: 'Conectar com a Blaze' }),
    );

    await waitFor(() => expect(mockStartOAuth).toHaveBeenCalledTimes(1));
    expect(openSpy).toHaveBeenCalledWith(
      'https://id.blaze.test/x',
      '_blank',
      'noopener,noreferrer',
    );
    expect(mockAddToast).toHaveBeenCalledWith(
      'success',
      'Página de autenticação aberta',
    );
    // botao volta ao estado habilitado apos a acao terminar
    expect(
      await screen.findByRole('button', { name: 'Conectar com a Blaze' }),
    ).toBeEnabled();
    openSpy.mockRestore();
  });

  it('shows account info, refresh/disconnect actions, valid token and scopes when connected', async () => {
    mockGetOAuthSession.mockResolvedValue(connectedOAuth());
    await renderPage();

    expect(
      await screen.findByRole('button', { name: 'Renovar Sessão' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Desconectar Conta' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Streamer Exemplo')).toBeInTheDocument();
    expect(screen.getByText('@streamer')).toBeInTheDocument();
    expect(screen.getByText('Token: Válido')).toBeInTheDocument();
    expect(
      screen.getByText('Scopes: users.read, offline.access'),
    ).toBeInTheDocument();
  });

  it('disconnects the account and toasts success', async () => {
    mockGetOAuthSession.mockResolvedValue(connectedOAuth());
    mockDisconnectOAuth.mockResolvedValue(
      oauthAction({ status: 'disconnected', refreshed: false, disconnected: true, connected: false }),
    );
    await renderPage();

    fireEvent.click(
      await screen.findByRole('button', { name: 'Desconectar Conta' }),
    );

    await waitFor(() => expect(mockDisconnectOAuth).toHaveBeenCalledTimes(1));
    expect(mockAddToast).toHaveBeenCalledWith('success', 'Conta desconectada');
  });

  it('renders the setup checklist, recommended scopes table and next steps', async () => {
    mockGetSetupStatus.mockResolvedValue(
      setupStatus({
        checklist: [
          { code: 'client_id', label: 'Client ID', status: 'ok', help: 'h1' },
          { code: 'client_secret', label: 'Secret', status: 'missing', help: 'h2' },
        ],
        recommendedScopes: [
          { name: 'users.read', phase: 'MVP_3', requiredNow: false, reason: 'ler usuarios' },
        ],
        nextSteps: ['Primeiro passo'],
      }),
    );
    await renderPage();

    expect(await screen.findByText('Checklist de Configuração')).toBeInTheDocument();
    expect(screen.getByText('Client ID')).toBeInTheDocument();
    expect(screen.getByText('Secret')).toBeInTheDocument();
    expect(screen.getByText('ok')).toBeInTheDocument();
    expect(screen.getByText('missing')).toBeInTheDocument();

    const scopesTable = screen.getByRole('table', { name: 'Scopes Recomendados' });
    const scopeRow = within(scopesTable).getByText('users.read').closest('tr')!;
    expect(within(scopeRow).getByText('ler usuarios')).toBeInTheDocument();
    expect(within(scopeRow).getByText('MVP_3')).toBeInTheDocument();

    expect(screen.getByText('Próximos passos:')).toBeInTheDocument();
    expect(screen.getByText('1. Primeiro passo')).toBeInTheDocument();
  });

  it('shows the error banner and toasts when status loading fails', async () => {
    mockGetStatus.mockRejectedValue(new Error('status fora'));
    await renderPage();

    expect(await screen.findByText('status fora')).toBeInTheDocument();
    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('error', 'status fora'),
    );
    // a pagina E o AccountFooter da Sidebar fazem polling de getStatus de
    // forma independente -> cada usePolling dispara seu proprio toast de
    // primeiro erro (2 chamadas de addToast neste seam). A deduplicacao que o
    // usuario ve acontece dentro do Toast (Toast.test.tsx: dedup por
    // tipo+texto enquanto visivel); aqui o modulo esta mockado, entao a
    // caracterizacao e das 2 chamadas.
    await waitFor(() =>
      expect(
        mockAddToast.mock.calls.filter(
          ([type, msg]) => type === 'error' && msg === 'status fora',
        ),
      ).toHaveLength(2),
    );
  });
});
