import type {
  CreateEventRequest,
  EventHistoryResponse,
  EventLifecycleStats,
  EventParticipantResponse,
  EventResponse,
  EventResultResponse,
  MemberProfile,
  OAuthActionResponse,
  OAuthSessionResponse,
  OAuthStartResponse,
  UpdateEventRequest,
} from './types';

export type {
  CreateEventRequest,
  EventHistoryResponse,
  EventLifecycleStats,
  EventParticipantResponse,
  EventResponse,
  EventResultResponse,
  MemberProfile,
  OAuthActionResponse,
  OAuthSessionResponse,
  OAuthStartResponse,
  UpdateEventRequest,
};

interface ApiErrorPayload {
  code?: string;
  message?: string;
  detail?: string;
  error?: string;
}

type ApiEvent = Omit<EventResponse, 'status'> & { status: string };
type ApiStats = Omit<EventLifecycleStats, 'status'> & { status: string };

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body != null) headers.set('Content-Type', 'application/json');

  const response = await fetch(url, {
    ...options,
    headers,
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const raw = await response.text().catch(() => '');
    let payload: ApiErrorPayload = {};
    try {
      payload = raw ? JSON.parse(raw) as ApiErrorPayload : {};
    } catch {
      payload = {};
    }
    const message = payload.message || payload.detail || payload.error || raw || response.statusText;
    throw new ApiError(response.status, payload.code || `HTTP_${response.status}`, message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

function normalizeStatus(value: string): EventResponse['status'] {
  const normalized = value.toUpperCase();
  if (normalized === 'DRAFT' || normalized === 'OPEN' || normalized === 'FINALIZING' || normalized === 'CLOSED'
    || normalized === 'COMPLETED' || normalized === 'CANCELLED') {
    return normalized;
  }
  throw new Error(`Status de evento desconhecido: ${value}`);
}

function normalizeEvent(event: ApiEvent): EventResponse {
  return {
    ...event,
    description: event.description || '',
    xPostUrl: event.xPostUrl || null,
    status: normalizeStatus(event.status),
  };
}

function normalizeStats(stats: ApiStats): EventLifecycleStats {
  return { ...stats, status: normalizeStatus(stats.status) };
}

const normalizeEvents = (events: ApiEvent[]) => events.map(normalizeEvent);

export const getOAuthSession = () => request<OAuthSessionResponse>('/api/blaze/oauth/session');
export const startOAuth = () => request<OAuthStartResponse>('/api/blaze/oauth/start');
export const refreshOAuth = () => request<OAuthActionResponse>('/api/blaze/oauth/refresh', { method: 'POST' });
export const disconnectOAuth = () => request<OAuthActionResponse>('/api/blaze/oauth/disconnect', { method: 'POST' });

export const getMe = () => request<MemberProfile>('/api/members/me');

export async function getEvents(status?: EventResponse['status']) {
  const query = status ? `?status=${encodeURIComponent(status.toLowerCase())}` : '';
  return normalizeEvents(await request<ApiEvent[]>(`/api/events${query}`));
}

export async function getEvent(id: string) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}`));
}

export async function getEventStats(id: string) {
  return normalizeStats(await request<ApiStats>(`/api/events/${encodeURIComponent(id)}/stats`));
}

export async function getMyEventHistory(): Promise<EventHistoryResponse> {
  const history = await request<{
    drafts?: ApiEvent[];
    active?: ApiEvent[];
    upcoming?: ApiEvent[];
    past?: ApiEvent[];
  }>('/api/events/my/history');
  return {
    drafts: normalizeEvents(history.drafts || []),
    active: normalizeEvents(history.active || history.upcoming || []),
    past: normalizeEvents(history.past || []),
  };
}

export async function createEvent(data: CreateEventRequest) {
  return normalizeEvent(await request<ApiEvent>('/api/events', {
    method: 'POST',
    body: JSON.stringify(data),
  }));
}

export async function updateEvent(id: string, data: UpdateEventRequest) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }));
}

export async function openEvent(id: string) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}/open`, { method: 'POST' }));
}

export async function finalizeEvent(id: string) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}/finalize`, { method: 'POST' }));
}

export async function cancelEvent(id: string) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}/cancel`, { method: 'POST' }));
}

export const getEventParticipants = (id: string) => request<EventParticipantResponse[]>(
  `/api/events/${encodeURIComponent(id)}/participants`,
);

export const executeDraw = (id: string) => request<EventResultResponse>(
  `/api/events/${encodeURIComponent(id)}/draw`,
  { method: 'POST' },
);

export const getEventResult = (id: string) => request<EventResultResponse>(
  `/api/events/${encodeURIComponent(id)}/winner`,
);

export interface ActionRuleResponse {
  id: string;
  eventId: string;
  actionType: string;
  enabled: boolean;
  weight: number;
  mode: string;
  createdAt: string;
}

export const getActionRules = (id: string) => request<ActionRuleResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-rules`,
);

export const updateActionRules = (id: string, actionTypes: string[], weights?: Record<string, number>, mode?: string) => request<ActionRuleResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-rules`,
  { method: 'PUT', body: JSON.stringify({ actionTypes, ...weights && { weights }, ...(mode && { mode }) }) },
);

export interface ActionTierResponse {
  id: string;
  eventId: string;
  actionType: string;
  threshold: number;
  entries: number;
  mode: string;
  createdAt: string;
}

export const getActionTiers = (id: string) => request<ActionTierResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-tiers`,
);

export const updateActionTiers = (
  id: string,
  tiers: Array<{ actionType: string; threshold: number; entries: number; mode?: string }>,
) => request<ActionTierResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-tiers`,
  { method: 'PUT', body: JSON.stringify({ tiers }) },
);
