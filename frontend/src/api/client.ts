const BASE = '';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`API ${res.status}: ${text || res.statusText}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

/* Health & Status */
export const getHealth = () => request<Record<string, string>>('/api/health');
export const getStatus = () => request<import('./types').StatusResponse>('/api/status');

/* Setup */
export const getSetupStatus = () => request<import('./types').BlazeSetupStatusResponse>('/api/blaze/setup');

/* OAuth */
export const getOAuthSession = () => request<import('./types').OAuthSessionResponse>('/api/blaze/oauth/session');
export const startOAuth = () => request<import('./types').OAuthStartResponse>('/api/blaze/oauth/start');
export const refreshOAuth = () =>
  request<import('./types').OAuthActionResponse>('/api/blaze/oauth/refresh', { method: 'POST' });
export const disconnectOAuth = () =>
  request<import('./types').OAuthActionResponse>('/api/blaze/oauth/disconnect', { method: 'POST' });

/* Events */
export const getEventsStatus = () =>
  request<import('./types').BlazeEventsStatusResponse>('/api/blaze/events/status');
export const startEvents = () =>
  request<import('./types').BlazeEventsStatusResponse>('/api/blaze/events/start', { method: 'POST' });
export const stopEvents = () =>
  request<import('./types').BlazeEventsStatusResponse>('/api/blaze/events/stop', { method: 'POST' });

/* Overlay Profiles */
export const getOverlayProfiles = () =>
  request<import('./types').OverlayProfile[]>('/api/overlay-profiles');
export const createOverlayProfile = (data: { name: string; description?: string }) =>
  request<import('./types').OverlayProfile>('/api/overlay-profiles', {
    method: 'POST',
    body: JSON.stringify(data),
  });
export const updateOverlayProfile = (id: string, data: { name: string; description?: string }) =>
  request<import('./types').OverlayProfile>(`/api/overlay-profiles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
export const deleteOverlayProfile = (id: string) =>
  request<void>(`/api/overlay-profiles/${id}`, { method: 'DELETE' });

/* Overlays */
export const getOverlays = (profileId: string) =>
  request<import('./types').Overlay[]>(`/api/overlay-profiles/${profileId}/overlays`);
export const getOverlay = (overlayId: string) =>
  request<import('./types').Overlay>(`/api/overlays/${overlayId}`);
export const createOverlay = (profileId: string, data: { name: string; type?: string }) =>
  request<import('./types').Overlay>(`/api/overlay-profiles/${profileId}/overlays`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
export const updateOverlay = (overlayId: string, data: Record<string, unknown>) =>
  request<import('./types').Overlay>(`/api/overlays/${overlayId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
export const deleteOverlay = (overlayId: string) =>
  request<void>(`/api/overlays/${overlayId}`, { method: 'DELETE' });

/* Overlay Layers */
export const getOverlayLayers = (overlayId: string) =>
  request<import('./types').OverlayLayer[]>(`/api/overlays/${overlayId}/layers`);
export const createOverlayLayer = (overlayId: string, data: Record<string, unknown>) =>
  request<import('./types').OverlayLayer>(`/api/overlays/${overlayId}/layers`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
export const deleteOverlayLayer = (overlayId: string, layerId: string) =>
  request<void>(`/api/overlays/${overlayId}/layers/${layerId}`, { method: 'DELETE' });

/* Overlay Manifest (public) */
export const getOverlayManifest = (publicToken: string) =>
  request<import('./types').OverlayManifestResponse>(`/api/public/overlays/${publicToken}/manifest`);

/* Blaze API */
export const getBlazeProfile = () =>
  request<Record<string, unknown>>('/api/blaze/users/profile');
export const getBlazeChannel = (slug: string) =>
  request<Record<string, unknown>>(`/api/blaze/channels?slug=${encodeURIComponent(slug)}`);

export default {
  getHealth,
  getStatus,
  getSetupStatus,
  getOAuthSession,
  startOAuth,
  refreshOAuth,
  disconnectOAuth,
  getEventsStatus,
  startEvents,
  stopEvents,
  getOverlayProfiles,
  createOverlayProfile,
  updateOverlayProfile,
  deleteOverlayProfile,
  getOverlays,
  getOverlay,
  createOverlay,
  updateOverlay,
  deleteOverlay,
  getOverlayLayers,
  createOverlayLayer,
  deleteOverlayLayer,
  getOverlayManifest,
  getBlazeProfile,
  getBlazeChannel,
};
