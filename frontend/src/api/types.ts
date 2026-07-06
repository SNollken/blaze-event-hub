/* Blaze Event Hub — API Types (matches client.ts) */

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
  profilePresent: boolean;
  scopes: string[];
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
  creatorChannelId: string;
  title: string;
  description: string;
  status: string;
  rulesMode: string;
  maxEntriesPerParticipant: number;
  requiresInterestBeforeAction: boolean;
  startsAt: string | null;
  endsAt: string | null;
  rules?: RuleResponse[];
}

export interface RuleResponse {
  id: string;
  actionType: string;
  thresholdAmount: number;
  entries: number;
  isActive: boolean;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  prizeType?: string;
  prizeDescription?: string;
  rulesMode?: string;
  maxEntriesPerParticipant?: number;
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

export interface ParticipantResponse {
  memberId: string;
  blazeUsername: string;
  displayName: string;
  status: string;
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
