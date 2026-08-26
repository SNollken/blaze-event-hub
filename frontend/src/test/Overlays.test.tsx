import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Overlay, OverlayProfile, StatusResponse } from '../api/types';
import Overlays from '../pages/Overlays';

const mockGetStatus = vi.hoisted(() => vi.fn());
const mockGetOverlayProfiles = vi.hoisted(() => vi.fn());
const mockGetOverlays = vi.hoisted(() => vi.fn());
const mockCreateOverlayProfile = vi.hoisted(() => vi.fn());
const mockDeleteOverlayProfile = vi.hoisted(() => vi.fn());
const mockDeleteOverlay = vi.hoisted(() => vi.fn());
const mockAddToast = vi.hoisted(() => vi.fn());
const mockWriteText = vi.hoisted(() => vi.fn());

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    getStatus: mockGetStatus,
    getOverlayProfiles: mockGetOverlayProfiles,
    getOverlays: mockGetOverlays,
    createOverlayProfile: mockCreateOverlayProfile,
    deleteOverlayProfile: mockDeleteOverlayProfile,
    deleteOverlay: mockDeleteOverlay,
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

const profileFixture: OverlayProfile = {
  id: 'p1',
  name: 'Perfil OBS',
  description: null,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
};

const overlayFixture: Overlay = {
  id: 'o1',
  profileId: 'p1',
  name: 'Alerts overlay',
  type: 'ALERTS',
  publicToken: 'tok-123',
  enabled: true,
  config: {
    canvasWidth: 1920,
    canvasHeight: 1080,
    backgroundMode: 'SOLID',
    backgroundColor: '#000000',
    transparent: false,
    defaultFontFamily: 'sans',
    defaultTextColor: '#fff',
  },
  layers: [],
  assets: [],
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
};

async function renderPage() {
  let utils!: ReturnType<typeof render>;
  // act async flusha os fetches iniciais do usePolling (pagina + Sidebar)
  await act(async () => {
    utils = render(
      <MemoryRouter initialEntries={['/overlays']}>
        <Overlays />
      </MemoryRouter>,
    );
  });
  return utils;
}

/** Renderiza com perfil carregado e abre a secao de overlays do perfil p1. */
async function renderWithOverlaysLoaded() {
  mockGetOverlays.mockResolvedValue([overlayFixture]);
  await renderPage();
  fireEvent.click(
    await screen.findByRole('button', { name: 'Ver overlays de Perfil OBS' }),
  );
  await screen.findByText('Alerts overlay');
}

describe('Overlays', () => {
  beforeEach(() => {
    mockGetStatus.mockReset().mockResolvedValue(status());
    mockGetOverlayProfiles.mockReset().mockResolvedValue([profileFixture]);
    mockGetOverlays.mockReset().mockResolvedValue([]);
    mockCreateOverlayProfile.mockReset().mockResolvedValue(profileFixture);
    mockDeleteOverlayProfile.mockReset().mockResolvedValue(undefined);
    mockDeleteOverlay.mockReset().mockResolvedValue(undefined);
    mockAddToast.mockReset();
    mockWriteText.mockReset().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: mockWriteText },
      configurable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true,
    });
  });

  it('shows the empty message when there are no profiles', async () => {
    mockGetOverlayProfiles.mockResolvedValue([]);
    await renderPage();

    expect(await screen.findByText('Nenhum perfil criado.')).toBeInTheDocument();
  });

  it('lists profiles and loads overlays of a profile via the view button', async () => {
    mockGetOverlays.mockResolvedValue([overlayFixture]);
    await renderPage();

    expect(await screen.findByText('Perfil OBS')).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', { name: 'Ver overlays de Perfil OBS' }),
    );

    await waitFor(() => expect(mockGetOverlays).toHaveBeenCalledWith('p1'));
    expect(await screen.findByText('Overlays do Perfil')).toBeInTheDocument();
    expect(screen.getByText('Alerts overlay')).toBeInTheDocument();
    expect(screen.getByText('ALERTS')).toBeInTheDocument();
    expect(screen.getByText('1920x1080')).toBeInTheDocument();
    expect(screen.getByText('tok-123')).toBeInTheDocument();
  });

  it('creates a profile through the modal and closes it on success', async () => {
    await renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Novo Perfil' }));

    expect(
      await screen.findByRole('heading', { name: 'Novo Perfil de Overlay' }),
    ).toBeInTheDocument();
    const createButton = screen.getByRole('button', { name: 'Criar Perfil' });
    expect(createButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Nome do Perfil'), {
      target: { value: 'Perfil novo' },
    });
    expect(createButton).toBeEnabled();

    fireEvent.click(createButton);

    await waitFor(() =>
      expect(mockCreateOverlayProfile).toHaveBeenCalledWith({
        name: 'Perfil novo',
        description: undefined,
      }),
    );
    expect(mockAddToast).toHaveBeenCalledWith('success', 'Perfil criado com sucesso');
    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'Novo Perfil de Overlay' }),
      ).not.toBeInTheDocument(),
    );
    // reloadProfiles() dispara um segundo getOverlayProfiles apos criar
    await waitFor(() => expect(mockGetOverlayProfiles).toHaveBeenCalledTimes(2));
  });

  it('deletes a profile and toasts success', async () => {
    await renderPage();
    await screen.findByText('Perfil OBS');

    fireEvent.click(
      screen.getByRole('button', { name: 'Remover perfil Perfil OBS' }),
    );

    await waitFor(() => expect(mockDeleteOverlayProfile).toHaveBeenCalledWith('p1'));
    expect(mockAddToast).toHaveBeenCalledWith('success', 'Perfil removido');
    await waitFor(() => expect(mockGetOverlayProfiles).toHaveBeenCalledTimes(2));
  });

  it('deletes an overlay, toasts success and removes the row', async () => {
    await renderWithOverlaysLoaded();

    fireEvent.click(
      screen.getByRole('button', { name: 'Remover overlay Alerts overlay' }),
    );

    await waitFor(() => expect(mockDeleteOverlay).toHaveBeenCalledWith('o1'));
    expect(mockAddToast).toHaveBeenCalledWith('success', 'Overlay removido');
    await waitFor(() =>
      expect(screen.queryByText('Alerts overlay')).not.toBeInTheDocument(),
    );
  });

  it('shows overlay details in a modal with canvas, background and public URL', async () => {
    await renderWithOverlaysLoaded();

    fireEvent.click(
      screen.getByRole('button', { name: 'Ver detalhes da overlay Alerts overlay' }),
    );

    expect(
      await screen.findByRole('heading', { name: 'Alerts overlay' }),
    ).toBeInTheDocument();
    expect(screen.getByText('1920 x 1080')).toBeInTheDocument();
    expect(screen.getByText('#000000')).toBeInTheDocument();
    expect(
      screen.getByText('http://localhost:3000/overlay/tok-123'),
    ).toBeInTheDocument();
  });

  it('copies the overlay public URL to the clipboard and toasts success', async () => {
    await renderWithOverlaysLoaded();

    fireEvent.click(
      screen.getByRole('button', { name: 'Copiar URL da overlay Alerts overlay' }),
    );

    await waitFor(() =>
      expect(mockWriteText).toHaveBeenCalledWith(
        'http://localhost:3000/overlay/tok-123',
      ),
    );
    expect(mockAddToast).toHaveBeenCalledWith('success', 'URL copiada');
  });

  it('shows the error banner and toasts when profile loading fails', async () => {
    mockGetOverlayProfiles.mockRejectedValue(new Error('perfis fora'));
    await renderPage();

    expect(await screen.findByText('perfis fora')).toBeInTheDocument();
    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('error', 'perfis fora'),
    );
    expect(mockAddToast).toHaveBeenCalledTimes(1);
  });
});
