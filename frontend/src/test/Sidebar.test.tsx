import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { StatusResponse } from '../api/types';
import { Sidebar } from '../components/Sidebar';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockStartOAuth = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, getStatus: mockGetStatus, startOAuth: mockStartOAuth };
});

vi.mock('../components/Toast', () => ({
  addToast: mockAddToast,
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

async function renderSidebar(initialPath = '/') {
  let utils!: ReturnType<typeof render>;
  // act async flushes the initial usePolling fetch (mock resolves in a
  // microtask) so the AccountFooter state update happens inside act.
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={[initialPath]}>
        <Sidebar />
      </MemoryRouter>,
    );
  });
  return utils;
}

describe('Sidebar', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status({}));
    mockStartOAuth.mockReset();
    mockAddToast.mockReset();
  });

  it('renders the 6 nav links with i18n labels', async () => {
    await renderSidebar();
    // locale default pt-BR (initI18n nao chamado em teste)
    for (const label of ['Início', 'Eventos ao Vivo', 'Blaze Channel', 'Alertas', 'Sorteios', 'Overlays']) {
      expect(await screen.findByRole('link', { name: new RegExp(label) })).toBeInTheDocument();
    }
    // prova que as chaves nav.* "mortas" do i18n-audit sao usadas via t() dinamico
    expect(screen.getByRole('link', { name: /Eventos ao Vivo/ })).toHaveAttribute('href', '/events');
    expect(screen.getByRole('link', { name: /Alertas/ })).toHaveAttribute('href', '/alerts');
    expect(screen.getByRole('link', { name: /Sorteios/ })).toHaveAttribute('href', '/giveaways');
  });

  it('exposes the aside landmark with aria-label', async () => {
    const { container } = await renderSidebar();
    const aside = container.querySelector('aside');
    expect(aside).not.toBeNull();
    expect(aside).toHaveAttribute('aria-label', 'Painel de Controle');
  });

  it("marks the dashboard link current on '/'", async () => {
    await renderSidebar('/');
    const home = await screen.findByRole('link', { name: /Início/ });
    expect(home).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /Sorteios/ })).not.toHaveAttribute('aria-current');
  });

  it("marks only the matching link current on '/giveaways'", async () => {
    await renderSidebar('/giveaways');
    const sorteios = await screen.findByRole('link', { name: /Sorteios/ });
    expect(sorteios).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /Início/ })).not.toHaveAttribute('aria-current');
  });

  it('polls status on mount and shows the connect button when disconnected', async () => {
    await renderSidebar();
    expect(await screen.findByRole('button', { name: /Conectar conta Blaze/ })).toBeEnabled();
    expect(mockGetStatus).toHaveBeenCalled();
  });

  it('connect click opens the authorization URL and toasts success', async () => {
    mockStartOAuth.mockResolvedValue({ authorizationUrl: 'https://id.blaze.test/oauth?code=x' });
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);
    await renderSidebar();

    fireEvent.click(await screen.findByRole('button', { name: /Conectar conta Blaze/ }));

    await waitFor(() => expect(mockStartOAuth).toHaveBeenCalledTimes(1));
    expect(openSpy).toHaveBeenCalledWith('https://id.blaze.test/oauth?code=x', '_blank', 'noopener,noreferrer');
    expect(mockAddToast).toHaveBeenCalledWith('success', 'Página de autenticação aberta');
    expect(await screen.findByRole('button', { name: /Conectar conta Blaze/ })).toBeEnabled();
    openSpy.mockRestore();
  });

  it('connect failure toasts the error message and re-enables the button', async () => {
    mockStartOAuth.mockRejectedValue(new Error('oauth indisponivel'));
    await renderSidebar();

    fireEvent.click(await screen.findByRole('button', { name: /Conectar conta Blaze/ }));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('error', 'oauth indisponivel'));
    expect(await screen.findByRole('button', { name: /Conectar conta Blaze/ })).toBeEnabled();
  });

  it('shows the connected account name when oauthConnected', async () => {
    mockGetStatus.mockResolvedValue(
      status({ oauthConnected: true, connectedAccountDisplayName: 'StreamerExemplo' }),
    );
    await renderSidebar();

    expect(await screen.findByText('StreamerExemplo')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Conectar conta Blaze/ })).not.toBeInTheDocument();
  });

  it("falls back to 'Conectado' when displayName is empty", async () => {
    mockGetStatus.mockResolvedValue(status({ oauthConnected: true, connectedAccountDisplayName: '' }));
    await renderSidebar();

    expect(await screen.findByText('Conectado')).toBeInTheDocument();
  });

  it("falls back to 'Conectado' when displayName is null (backend sem perfil)", async () => {
    mockGetStatus.mockResolvedValue(
      status({ oauthConnected: true, connectedAccountDisplayName: null }),
    );
    await renderSidebar();

    expect(await screen.findByText('Conectado')).toBeInTheDocument();
  });
});
