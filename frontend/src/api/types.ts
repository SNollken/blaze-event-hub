/* Blaze Event Hub - API Types (matches client.ts) */

export type EventStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'DRAWING' | 'COMPLETED' | 'CANCELLED';

export interface StatusResponse {
  appName: string;
  version: string;
  javaVersion: string;
  blazeOAuthConfigured: boolean;
  eventsRunning: boolean;
  sessionIdPresent: boolean;
  oauthConnected: boolean;
  profilePresent: boolean;
  connectedAccountDisplayName: string | null;
  uptimeSeconds: number;
  nextRecommendedAction: string | null;
}

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
    rawAvailable?: boolean;
  } | null;
  tokenType?: string | null;
  userId?: string | null;
  scopes: string[];
  expiresAt?: string | null;
  tokenExpiredOrUnknown?: boolean;
  lastConnectedAt?: string | null;
  lastProfileSyncAt?: string | null;
  nextRecommendedAction: string | null;
}

export interface OAuthStartResponse {
  authorizationUrl: string;
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
  creatorMemberId: string;
  creatorBlazeUserId?: string;
  creatorChannelId: string;
  channelSlug?: string;
  title: string;
  description: string;
  prizeType?: string;
  prizeDescription?: string;
  status: string;
  mode?: string;
  maxEntries?: number;
  rulesMode: string;
  maxEntriesPerParticipant: number;
  requiresInterestBeforeAction: boolean;
  startsAt: string | null;
  endsAt: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  closedAt?: string | null;
  completedAt?: string | null;
  participantCount?: number;
  rules?: RuleResponse[];
}

export interface EventStatsLast24h {
  votes?: number;
  subs?: number;
  giftedSubs?: number;
}

export interface EventStatsResponse {
  totalVotes: number;
  totalSubs: number;
  totalGiftedSubs: number;
  participants: number;
  totalEntries: number;
  last24h: EventStatsLast24h | number;
}

export interface EventHistoryResponse {
  drafts: EventResponse[];
  upcoming: EventResponse[];
  past: EventResponse[];
}

export interface RuleResponse {
  id: string;
  eventId?: string;
  actionType: string;
  thresholdAmount: number;
  entries: number;
  isActive: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  prizeType?: string;
  prizeDescription?: string;
  rulesMode?: string;
  maxEntriesPerParticipant?: number;
  requiresInterestBeforeAction?: boolean;
  startsAt?: string;
  endsAt?: string;
  creatorChannelId: string;
  rules: CreateRuleRequest[];
}

export interface CreateRuleRequest {
  actionType: string;
  thresholdAmount: number;
  entries: number;
}

export interface UpdateRuleRequest {
  actionType?: string;
  thresholdAmount?: number;
  entries?: number;
  isActive?: boolean;
}

export type Rule = CreateRuleRequest;

export interface ParticipantResponse {
  memberId: string;
  blazeUsername: string;
  displayName: string;
  status: string;
  interestedAt?: string;
  lastCalculatedEntries: number;
}

export interface EntryResponse {
  id: string;
  eventId: string;
  memberId: string;
  actionType: string;
  amount: number;
  entriesGranted: number;
  calculationReason: string;
}

export interface WinnerResponse {
  id: string;
  eventId: string;
  memberId: string;
  entriesAtDrawTime: number;
  drawSeed: string;
  drawMethod: string;
  selectedAt: string;
  notes: string;
}
