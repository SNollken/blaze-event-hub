export type EventStatus = 'DRAFT' | 'OPEN' | 'FINALIZING' | 'CLOSED' | 'COMPLETED' | 'CANCELLED';

export type CaptureHealth = 'INACTIVE' | 'STARTING' | 'HEALTHY' | 'DEGRADED' | 'FINALIZING';

export interface OAuthSessionResponse {
  connected: boolean;
  tokenPresent: boolean;
  refreshCredentialPresent?: boolean;
  profilePresent: boolean;
  profile?: {
    id: string;
    username: string;
    displayName: string;
    avatarUrl: string | null;
  } | null;
  scopes: string[];
  expiresAt?: string | null;
  nextRecommendedAction: string | null;
}

export interface OAuthStartResponse {
  authorizationUrl: string;
}

export interface OAuthActionResponse {
  status?: string;
  refreshed?: boolean;
  disconnected?: boolean;
  connected?: boolean;
  message: string;
}

export interface MemberProfile {
  id: string;
  blazeUserId: string;
  blazeUsername: string;
  displayName: string;
  avatarUrl: string | null;
  status: string;
}

export interface EventResponse {
  id: string;
  creatorChannelId: string;
  creatorChannelSlug: string | null;
  creatorChannelDisplayName: string | null;
  creatorChannelAvatarUrl: string | null;
  title: string;
  description: string;
  prize: string;
  xPostUrl: string | null;
  entryCommand: string;
  status: EventStatus;
  finalizedParticipantCount: number;
  finalizedPoolHash: string | null;
  enabledActionTypes: string[];
  startsAt: string | null;
  endsAt: string | null;
  createdAt: string;
  updatedAt: string;
  openedAt: string | null;
  finalizationCutoffAt: string | null;
  closedAt: string | null;
  completedAt: string | null;
}

export interface EventLifecycleStats {
  eventId: string;
  status: EventStatus;
  participantCount: number;
  finalizedParticipantCount: number;
  captureActive: boolean;
  captureHealth: CaptureHealth;
  lastPolledAt: string | null;
  lastSuccessfulPollAt: string | null;
  lastErrorCode: string | null;
  canFinalize: boolean;
  canDraw: boolean;
  openedAt: string | null;
  finalizationCutoffAt: string | null;
  closedAt: string | null;
  completedAt: string | null;
}

export interface EventHistoryResponse {
  drafts: EventResponse[];
  active: EventResponse[];
  past: EventResponse[];
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  prize: string;
  xPostUrl?: string;
  entryCommand: string;
  startsAt?: string;
  endsAt?: string;
}

export interface UpdateEventRequest {
  title?: string;
  description?: string;
  prize?: string;
  xPostUrl?: string;
  entryCommand?: string;
  startsAt?: string;
  endsAt?: string;
}

export interface EventParticipantResponse {
  blazeUserId: string;
  blazeUsername: string | null;
  displayName: string;
  actionType: string;
  entryWeight: number;
  enteredAt: string;
}

export interface EventResultResponse {
  eventId: string;
  winnerUsername: string | null;
  winnerDisplayName: string;
  drawSeed: string;
  drawMethod: string;
  poolHash: string;
  participantCount: number;
  selectedAt: string;
}
