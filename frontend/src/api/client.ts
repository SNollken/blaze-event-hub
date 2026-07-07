import type {
  StatusResponse,
  OAuthSessionResponse,
  OAuthStartResponse,
  MemberProfile,
  EventResponse,
  CreateEventRequest,
  ParticipantResponse,
  EntryResponse,
  WinnerResponse,
  RuleResponse,
} from './types';

export type {
  StatusResponse,
  OAuthSessionResponse,
  OAuthStartResponse,
  MemberProfile,
  EventResponse,
  CreateEventRequest,
  ParticipantResponse,
  EntryResponse,
  WinnerResponse,
  RuleResponse,
};

const BASE = '';
const API_KEY = import.meta.env.VITE_NOLLEN_API_KEY || 'dev-local-key';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'X-Nollen-Api-Key': API_KEY,
      ...(options?.headers || {}),
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

/* Status */
export const getStatus = () => request<StatusResponse>('/api/status');

/* OAuth */
export const getOAuthSession = () => request<OAuthSessionResponse>('/api/blaze/oauth/session');
export const startOAuth = () => request<OAuthStartResponse>('/api/blaze/oauth/start');

/* Members */
export const getMe = () => request<MemberProfile>('/api/members/me');

/* Events */
export const getEvents = (status?: string) =>
  request<EventResponse[]>(`/api/events${status ? `?status=${status}` : ''}`);
export const getEvent = (id: string) => request<EventResponse>(`/api/events/${id}`);
export const createEvent = (data: CreateEventRequest) =>
  request<EventResponse>('/api/events', { method: 'POST', body: JSON.stringify(data) });
export const openEvent = (id: string) =>
  request<EventResponse>(`/api/events/${id}/open`, { method: 'POST' });
export const closeEvent = (id: string) =>
  request<EventResponse>(`/api/events/${id}/close`, { method: 'POST' });
export const cancelEvent = (id: string) =>
  request<EventResponse>(`/api/events/${id}/cancel`, { method: 'POST' });

/* Interest */
export const expressInterest = (eventId: string) =>
  request<unknown>(`/api/events/${eventId}/interest`, { method: 'POST' });
export const withdrawInterest = (eventId: string) =>
  request<void>(`/api/events/${eventId}/interest`, { method: 'DELETE' });
export const getParticipants = (eventId: string) =>
  request<ParticipantResponse[]>(`/api/events/${eventId}/participants`);

/* Entries */
export const getEntries = (eventId: string) =>
  request<EntryResponse[]>(`/api/events/${eventId}/entries`);
export const recalculate = (eventId: string) =>
  request<number>(`/api/events/${eventId}/recalculate`, { method: 'POST' });

/* Draw */
export const executeDraw = (eventId: string) =>
  request<WinnerResponse>(`/api/events/${eventId}/draw`, { method: 'POST' });
export const getWinner = (eventId: string) =>
  request<WinnerResponse>(`/api/events/${eventId}/winner`);
