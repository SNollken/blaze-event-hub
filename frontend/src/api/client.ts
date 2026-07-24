import type {
  ActionRuleResponse,
  ActionTierResponse,
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
  SocketStatus,
  UpdateActionRulesRequest,
  UpdateActionTiersRequest,
  UpdateEventRequest,
} from './types';
import { ApiError } from './types';

export type {
  ActionRuleResponse,
  ActionTierResponse,
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
  SocketStatus,
  UpdateActionRulesRequest,
  UpdateActionTiersRequest,
  UpdateEventRequest,
};
export { ApiError };

interface ApiErrorPayload {
  code?: string;
  message?: string;
  detail?: string;
  error?: string;
}

type ApiEvent = Omit<EventResponse, 'status'> & { status: string };
type ApiStats = Omit<EventLifecycleStats, 'status'> & { status: string };

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body != null) headers.set('Content-Type', 'application/json');

  const response = await fetch(url, {
    ...options,
    headers,
    credentials: 'same-origin',
    signal: options.signal,
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

export const getOAuthSession = (signal?: AbortSignal) => request<OAuthSessionResponse>('/api/blaze/oauth/session', { signal });
export const startOAuth = (signal?: AbortSignal) => request<OAuthStartResponse>('/api/blaze/oauth/start', { signal });
export const refreshOAuth = (signal?: AbortSignal) => request<OAuthActionResponse>('/api/blaze/oauth/refresh', { method: 'POST', signal });
export const disconnectOAuth = (signal?: AbortSignal) => request<OAuthActionResponse>('/api/blaze/oauth/disconnect', { method: 'POST', signal });

export const getMe = (signal?: AbortSignal) => request<MemberProfile>('/api/members/me', { signal });

export async function getEvents(status?: EventResponse['status'], signal?: AbortSignal) {
  const query = status ? `?status=${encodeURIComponent(status.toLowerCase())}` : '';
  return normalizeEvents(await request<ApiEvent[]>(`/api/events${query}`, { signal }));
}

export async function getEvent(id: string, signal?: AbortSignal) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}`, { signal }));
}

export async function getEventStats(id: string, signal?: AbortSignal) {
  return normalizeStats(await request<ApiStats>(`/api/events/${encodeURIComponent(id)}/stats`, { signal }));
}

export async function getMyEventHistory(signal?: AbortSignal): Promise<EventHistoryResponse> {
  const history = await request<{
    drafts?: ApiEvent[];
    active?: ApiEvent[];
    upcoming?: ApiEvent[];
    past?: ApiEvent[];
  }>('/api/events/my/history', { signal });
  return {
    drafts: normalizeEvents(history.drafts || []),
    active: normalizeEvents(history.active || history.upcoming || []),
    past: normalizeEvents(history.past || []),
  };
}

export async function createEvent(data: CreateEventRequest, signal?: AbortSignal) {
  return normalizeEvent(await request<ApiEvent>('/api/events', {
    method: 'POST',
    body: JSON.stringify(data),
    signal,
  }));
}

export async function updateEvent(id: string, data: UpdateEventRequest, signal?: AbortSignal) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
    signal,
  }));
}

export async function openEvent(id: string, signal?: AbortSignal) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}/open`, { method: 'POST', signal }));
}

export async function finalizeEvent(id: string, signal?: AbortSignal) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}/finalize`, { method: 'POST', signal }));
}

export async function cancelEvent(id: string, signal?: AbortSignal) {
  return normalizeEvent(await request<ApiEvent>(`/api/events/${encodeURIComponent(id)}/cancel`, { method: 'POST', signal }));
}

export const getEventParticipants = (id: string, signal?: AbortSignal) => request<EventParticipantResponse[]>(
  `/api/events/${encodeURIComponent(id)}/participants`,
  { signal }
);

export const executeDraw = (id: string, signal?: AbortSignal) => request<EventResultResponse>(
  `/api/events/${encodeURIComponent(id)}/draw`,
  { method: 'POST', signal },
);

export const getEventResult = (id: string, signal?: AbortSignal) => request<EventResultResponse>(
  `/api/events/${encodeURIComponent(id)}/winner`,
  { signal }
);

export const getActionRules = (id: string, signal?: AbortSignal) => request<ActionRuleResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-rules`,
  { signal }
);

export const updateActionRules = (id: string, actionTypes: string[], weights?: Record<string, number>, mode?: string, signal?: AbortSignal) => request<ActionRuleResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-rules`,
  { method: 'PUT', body: JSON.stringify({ actionTypes, ...weights && { weights }, ...(mode && { mode }) }), signal },
);

export const getActionTiers = (id: string, signal?: AbortSignal) => request<ActionTierResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-tiers`,
  { signal }
);

export const updateActionTiers = (
  id: string,
  tiers: Array<{ actionType: string; threshold: number; entries: number; mode?: string }>,
  signal?: AbortSignal
) => request<ActionTierResponse[]>(
  `/api/events/${encodeURIComponent(id)}/action-tiers`,
  { method: 'PUT', body: JSON.stringify({ tiers }), signal },
);

export const addManualParticipant = (id: string, blazeUsername: string, actionType?: string, amount?: number, signal?: AbortSignal) =>
  request<EventParticipantResponse>(
    `/api/events/${encodeURIComponent(id)}/participants`,
    { method: 'POST', body: JSON.stringify({ blazeUsername, actionType, amount }), signal },
  );

export const getSocketStatus = (memberId?: string, signal?: AbortSignal) => {
  const params = memberId ? `?memberId=${encodeURIComponent(memberId)}` : '';
  return request<SocketStatus>(`/api/events/socket/status${params}`, { signal });
};