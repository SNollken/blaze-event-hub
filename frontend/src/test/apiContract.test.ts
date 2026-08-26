import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  disconnectOAuth,
  getOverlay,
  getOverlayManifest,
  refreshOAuth,
  startOAuth,
} from '../api/client';

/*
 * Contrato real dos endpoints (drift check r64).
 *
 * Cada payload abaixo é cópia literal do record Java correspondente no
 * backend (nomes de campo, tipos e nullabilidade). O teste passa pelo
 * client REAL (sem mock de módulo) com fetch stubado, então o tsc valida
 * que os tipos TS aceitam exatamente o formato que o Spring serializa.
 *
 * Records de referência:
 * - oauth/OAuthActionResponse.java (11 campos: NÃO existe "success")
 * - oauth/OAuthStartResponse.java (authorizationUrl + scopes)
 * - overlays/OverlayManifestResponse.java (enabled, name, publicToken,
 *   config, layers ManifestLayer[], assets ManifestAsset[]; NÃO existe
 *   "overlayId" nem "type")
 * - overlays/OverlayAsset.java (originalFilename, storedFilename,
 *   mimeType, width/height/checksum nullable; NÃO existe "filename",
 *   "contentType" nem "publicUrl")
 */

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response;
}

const fetchMock = vi.fn();

function stubFetch(body: unknown) {
  fetchMock.mockReset().mockResolvedValue(jsonResponse(body));
  vi.stubGlobal('fetch', fetchMock);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

const oauthActionPayload = {
  status: 'refreshed',
  refreshed: true,
  disconnected: false,
  connected: true,
  tokenPresent: true,
  refreshCredentialPresent: true,
  profilePresent: true,
  profile: {
    id: 'user-1',
    username: 'streamer',
    displayName: 'Streamer da Silva',
    avatarUrl: null,
  },
  expiresAt: '2026-08-26T18:00:00Z',
  nextRecommendedAction: 'READY_FOR_EVENTS',
  message: 'Sessão renovada.',
};

describe('contrato OAuthActionResponse (POST /api/blaze/oauth/refresh|disconnect)', () => {
  it('refresh devolve os 11 campos do record (sem campo success)', async () => {
    stubFetch(oauthActionPayload);

    const res = await refreshOAuth();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/blaze/oauth/refresh',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(res.status).toBe('refreshed');
    expect(res.refreshed).toBe(true);
    expect(res.disconnected).toBe(false);
    expect(res.connected).toBe(true);
    expect(res.tokenPresent).toBe(true);
    expect(res.refreshCredentialPresent).toBe(true);
    expect(res.profilePresent).toBe(true);
    expect(res.profile?.username).toBe('streamer');
    expect(res.expiresAt).toBe('2026-08-26T18:00:00Z');
    expect(res.nextRecommendedAction).toBe('READY_FOR_EVENTS');
    expect(res.message).toBe('Sessão renovada.');
    expect('success' in res).toBe(false);
  });

  it('disconnect devolve o mesmo record com status disconnected', async () => {
    stubFetch({ ...oauthActionPayload, status: 'disconnected', refreshed: false, disconnected: true });

    const res = await disconnectOAuth();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/blaze/oauth/disconnect',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(res.status).toBe('disconnected');
    expect(res.disconnected).toBe(true);
  });
});

describe('contrato OAuthStartResponse (POST /api/blaze/oauth/start)', () => {
  it('devolve authorizationUrl + scopes', async () => {
    stubFetch({
      authorizationUrl: 'https://id.blaze.test/oauth/authorize?x=1',
      scopes: ['users.read', 'offline.access'],
    });

    const res = await startOAuth();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/blaze/oauth/start',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(res.authorizationUrl).toBe('https://id.blaze.test/oauth/authorize?x=1');
    expect(res.scopes).toEqual(['users.read', 'offline.access']);
  });
});

const manifestPayload = {
  enabled: true,
  name: 'Alertas OBS',
  publicToken: 'a1b2c3d4e5f60718293a4b5c6d7e8f901234',
  config: {
    canvasWidth: 1920,
    canvasHeight: 1080,
    backgroundMode: 'solid',
    backgroundColor: '#000000',
    transparent: true,
    defaultFontFamily: 'Inter, Arial, sans-serif',
    defaultTextColor: '#ffffff',
  },
  layers: [
    {
      id: 'layer-1',
      type: 'TEXT',
      x: 10,
      y: 20,
      width: 300,
      height: 80,
      zIndex: 1,
      visible: true,
      opacity: 1,
      text: 'Novo seguidor!',
      assetId: null,
      style: {},
    },
  ],
  assets: [
    {
      id: 'asset-1',
      mimeType: 'image/png',
      publicUrl: '/api/public/overlays/a1b2c3d4e5f60718293a4b5c6d7e8f901234/assets/asset-1',
    },
  ],
};

describe('contrato OverlayManifestResponse (GET /api/public/overlays/{token}/manifest)', () => {
  it('devolve enabled/name/publicToken/config/layers/assets (sem overlayId nem type)', async () => {
    stubFetch(manifestPayload);

    const manifest = await getOverlayManifest(manifestPayload.publicToken);

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/public/overlays/${manifestPayload.publicToken}/manifest`,
      expect.anything(),
    );
    expect(manifest.enabled).toBe(true);
    expect(manifest.name).toBe('Alertas OBS');
    expect(manifest.publicToken).toBe(manifestPayload.publicToken);
    expect(manifest.config.canvasWidth).toBe(1920);
    expect(manifest.layers[0]?.type).toBe('TEXT');
    expect(manifest.assets[0]?.publicUrl).toBe(
      '/api/public/overlays/a1b2c3d4e5f60718293a4b5c6d7e8f901234/assets/asset-1',
    );
    expect(manifest.assets[0]?.mimeType).toBe('image/png');
    expect('overlayId' in manifest).toBe(false);
    expect('type' in manifest).toBe(false);
  });
});

describe('contrato OverlayAsset dentro de Overlay (GET /api/overlays/{id})', () => {
  it('assets usam originalFilename/storedFilename/mimeType/width/height/checksum', async () => {
    stubFetch({
      id: 'overlay-1',
      profileId: 'profile-1',
      name: 'Alertas OBS',
      type: 'alerts',
      publicToken: 'a1b2c3d4e5f60718293a4b5c6d7e8f901234',
      enabled: true,
      config: manifestPayload.config,
      layers: [],
      assets: [
        {
          id: 'asset-1',
          overlayId: 'overlay-1',
          originalFilename: 'logo.png',
          storedFilename: 'asset-1.png',
          mimeType: 'image/png',
          sizeBytes: 20480,
          width: 256,
          height: 256,
          checksum: 'sha256:abc123',
          createdAt: '2026-08-26T12:00:00Z',
        },
      ],
      createdAt: '2026-08-26T12:00:00Z',
      updatedAt: '2026-08-26T12:00:00Z',
    });

    const overlay = await getOverlay('overlay-1');
    const asset = overlay.assets[0];

    expect(asset?.originalFilename).toBe('logo.png');
    expect(asset?.storedFilename).toBe('asset-1.png');
    expect(asset?.mimeType).toBe('image/png');
    expect(asset?.sizeBytes).toBe(20480);
    expect(asset?.width).toBe(256);
    expect(asset?.height).toBe(256);
    expect(asset?.checksum).toBe('sha256:abc123');
    expect(asset && 'filename' in asset).toBe(false);
    expect(asset && 'contentType' in asset).toBe(false);
    expect(asset && 'publicUrl' in asset).toBe(false);
  });
});
