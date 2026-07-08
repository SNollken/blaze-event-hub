import type {
  StatusResponse,
  OAuthSessionResponse,
  OAuthStartResponse,
  MemberProfile,
  EventResponse,
  EventStatsResponse,
  EventHistoryResponse,
  CreateEventRequest,
  UpdateRuleRequest,
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
  EventStatsResponse,
  EventHistoryResponse,
  CreateEventRequest,
  UpdateRuleRequest,
  ParticipantResponse,
  EntryResponse,
  WinnerResponse,
  RuleResponse,
};

const BASE = '';
const API_KEY = import.meta.env.VITE_BLAZE_EVENT_HUB_API_KEY
  || import.meta.env.VITE_NOLLEN_API_KEY
  || 'dev-local-key';

type ApiEvent = EventResponse & Record<string, unknown>;

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

function normalizeStatus(value: unknown) {
  return String(value || 'DRAFT').toUpperCase();
}

function normalizeActionType(value: unknown) {
  return String(value || 'vote').toLowerCase();
}

function normalizeEvent(event: ApiEvent): EventResponse {
  const rules = event.rules?.map((rule) => ({
    ...rule,
    actionType: normalizeActionType(rule.actionType),
    isActive: rule.isActive ?? true,
  }));
  const rulesMode = String(event.rulesMode || event.mode || 'tier').toLowerCase();
  return {
    ...event,
    status: normalizeStatus(event.status),
    rulesMode,
    mode: String(event.mode || rulesMode),
    maxEntries: event.maxEntries ?? event.maxEntriesPerParticipant,
    description: event.description || '',
    rules,
  };
}

const normalizeEvents = (events: ApiEvent[]) => events.map(normalizeEvent);

/* Status */
export const getStatus = () => request<StatusResponse>('/api/status');

/* Blaze API */
export const getChannels = (slug: string) =>
  request<{ id: string; slug: string; displayName: string; avatarUrl: string }>(`/api/blaze/channels/resolve?slug=${encodeURIComponent(slug)}`);

/* OAuth */
export const getOAuthSession = () => request<OAuthSessionResponse>('/api/blaze/oauth/session');
export const startOAuth = () => request<OAuthStartResponse>('/api/blaze/oauth/start');

/* Members */
export const getMe = () => request<MemberProfile>('/api/members/me');

/* Events */
export const getEvents = async (status?: string) => {
  const events = await request<ApiEvent[]>(`/api/events${status ? `?status=${encodeURIComponent(status)}` : ''}`);
  return normalizeEvents(events);
};
export const getEvent = async (id: string) => normalizeEvent(await request<ApiEvent>(`/api/events/${id}`));
export const getEventStats = (id: string) => request<EventStatsResponse>(`/api/events/${id}/stats`);
export const getMyEventHistory = async () => {
  const history = await request<{ drafts?: ApiEvent[]; upcoming?: ApiEvent[]; past?: ApiEvent[] }>('/api/events/my/history');
  return {
    drafts: normalizeEvents(history.drafts || []),
    upcoming: normalizeEvents(history.upcoming || []),
    past: normalizeEvents(history.past || []),
  } satisfies EventHistoryResponse;
};
export const createEvent = async (data: CreateEventRequest) =>
  normalizeEvent(await request<ApiEvent>('/api/events', { method: 'POST', body: JSON.stringify(data) }));
export const updateEvent = async (id: string, data: Partial<CreateEventRequest>) =>
  normalizeEvent(await request<ApiEvent>(`/api/events/${id}`, { method: 'PUT', body: JSON.stringify(data) }));
export const openEvent = async (id: string) =>
  normalizeEvent(await request<ApiEvent>(`/api/events/${id}/open`, { method: 'POST' }));
export const closeEvent = async (id: string) =>
  normalizeEvent(await request<ApiEvent>(`/api/events/${id}/close`, { method: 'POST' }));
export const cancelEvent = async (id: string) =>
  normalizeEvent(await request<ApiEvent>(`/api/events/${id}/cancel`, { method: 'POST' }));

export const addEventRule = (eventId: string, data: UpdateRuleRequest) =>
  request<RuleResponse>(`/api/events/${eventId}/rules`, { method: 'POST', body: JSON.stringify(data) });
export const updateEventRule = (eventId: string, ruleId: string, data: UpdateRuleRequest) =>
  request<RuleResponse>(`/api/events/${eventId}/rules/${ruleId}`, { method: 'PATCH', body: JSON.stringify(data) });
export const removeEventRule = (eventId: string, ruleId: string) =>
  request<void>(`/api/events/${eventId}/rules/${ruleId}`, { method: 'DELETE' });
/* Interest */
export const expressInterest = (eventId: string) =>
  request<unknown>(`/api/events/${eventId}/interest`, { method: 'POST' });
export const withdrawInterest = (eventId: string) =>
  request<void>(`/api/events/${eventId}/interest`, { method: 'DELETE' });
export const getParticipants = (eventId: string) =>
  request<ParticipantResponse[]>(`/api/events/${eventId}/interest/participants`);

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
