/* Types matching the Spring Boot backend records */

export interface StatusResponse {
  appName: string;
  version: string;
  javaVersion: string;
  blazeOAuthConfigured: boolean;
  blazeApiConfigured: boolean;
  socketConfigured: boolean;
  tokenPresent: boolean;
  refreshCredentialPresent: boolean;
  monitoredChannelConfigured: boolean;
  eventsRunning: boolean;
  sessionIdPresent: boolean;
  activeProfilesCount: number;
  overlaysCount: number;
  uptimeSeconds: number;
  oauthConnected: boolean;
  profilePresent: boolean;
  connectedAccountDisplayName: string;
  connectedAccountId: string;
  lastProfileSyncAt: string | null;
  nextRecommendedAction: string | null;
}

export interface BlazeEventsStatusResponse {
  runnerRunning: boolean;
  clientRunning: boolean;
  sessionId: string | null;
  lastMessageType: string | null;
  startedAt: string | null;
}

export interface OAuthSessionResponse {
  connected: boolean;
  tokenPresent: boolean;
  refreshCredentialPresent: boolean;
  profilePresent: boolean;
  profile: OAuthProfileResponse | null;
  tokenType: string | null;
  userId: string | null;
  scopes: string[];
  expiresAt: string | null;
  tokenExpiredOrUnknown: boolean;
  lastConnectedAt: string | null;
  lastProfileSyncAt: string | null;
  nextRecommendedAction: string | null;
}

export interface OAuthProfileResponse {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface OAuthStartResponse {
  authorizationUrl: string;
}

export interface OAuthActionResponse {
  success: boolean;
  message: string;
}

export interface BlazeSetupStatusResponse {
  appName: string;
  environment: string;
  clientIdConfigured: boolean;
  clientIdMasked: string | null;
  clientCredentialConfigured: boolean;
  redirectUriConfigured: boolean;
  redirectUri: string | null;
  requestedScopes: string[];
  recommendedScopes: BlazeSetupScopeResponse[];
  tokenPresent: boolean;
  tokenExpiredOrUnknown: boolean;
  refreshCredentialPresent: boolean;
  oauthConnected: boolean;
  profilePresent: boolean;
  connectedAccountDisplayName: string | null;
  connectedAccountId: string | null;
  lastProfileSyncAt: string | null;
  nextRecommendedAction: string | null;
  monitoredChannelConfigured: boolean;
  monitoredChannel: string | null;
  eventsConfigReady: boolean;
  oauthStartReady: boolean;
  checklist: BlazeSetupItemResponse[];
  missingItems: BlazeSetupItemResponse[];
  nextSteps: string[];
  docsLinks: BlazeSetupDocsLinkResponse[];
  envExample: string | null;
}

export interface BlazeSetupScopeResponse {
  scope: string;
  recommended: boolean;
  description: string;
}

export interface BlazeSetupItemResponse {
  name: string;
  configured: boolean;
  status: string;
  help: string;
}

export interface BlazeSetupDocsLinkResponse {
  title: string;
  url: string;
}

export interface OverlayProfile {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OverlayLayer {
  id: string;
  overlayId: string;
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  zIndex: number;
  visible: boolean;
  opacity: number;
  text: string | null;
  assetId: string | null;
  style: Record<string, unknown>;
}

export interface OverlayAsset {
  id: string;
  overlayId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  publicUrl: string;
  createdAt: string;
}

export interface OverlayConfig {
  canvasWidth: number;
  canvasHeight: number;
  backgroundMode: string;
  backgroundColor: string;
  transparent: boolean;
  defaultFontFamily: string;
  defaultTextColor: string;
}

export interface Overlay {
  id: string;
  profileId: string;
  name: string;
  type: string;
  publicToken: string;
  enabled: boolean;
  config: OverlayConfig;
  layers: OverlayLayer[];
  assets: OverlayAsset[];
  createdAt: string;
  updatedAt: string;
}

export interface OverlayManifestResponse {
  overlayId: string;
  name: string;
  type: string;
  config: OverlayConfig;
  layers: OverlayLayer[];
}

export type BlazeEventType =
  | 'channel.follow'
  | 'channel.unfollow'
  | 'channel.subscribe'
  | 'channel.subscription.gift'
  | 'channel.vote'
  | 'channel.chat.message'
  | 'channel.chat.clear'
  | 'channel.chat.message_delete';

export type AlertCondition = 'ALWAYS' | 'MIN_AMOUNT' | 'RAID_MIN_SIZE';

export interface AlertRule {
  id: string;
  name: string;
  eventType: BlazeEventType;
  condition: AlertCondition;
  threshold: number;
  template: string | null;
  enabled: boolean;
  cooldownMs: number;
}

export interface AlertEvent {
  id: string;
  ruleId: string;
  ruleName: string;
  eventType: BlazeEventType;
  triggeredAt: string;
  message: string;
  acknowledged: boolean;
  metadata: Record<string, unknown>;
}

export interface AlertStatsResponse {
  totalRules: number;
  enabledRules: number;
  totalAlerts: number;
  unacknowledgedAlerts: number;
  acknowledgedAlerts: number;
  rules: AlertRule[];
}

export interface CreateAlertRuleRequest {
  name: string;
  eventType: BlazeEventType;
  condition: AlertCondition;
  threshold: number;
  template: string | null;
  enabled: boolean;
  cooldownMs: number;
}

export interface BlazeEventsLogEntry {
  id: string;
  timestamp: string;
  eventType: string;
  source: string;
  message: string;
  data: string | null;
}

export type GiveawayStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'DRAWING' | 'COMPLETED' | 'CANCELLED';

export interface Giveaway {
  id: string;
  title: string;
  description: string;
  status: GiveawayStatus;
  entryCount: number;
  maxEntries: number;
  createdAt: string;
  openedAt: string | null;
  closedAt: string | null;
  drawnAt: string | null;
  winnerIds: string[];
}

export interface GiveawayEntry {
  id: string;
  giveawayId: string;
  participantName: string;
  enteredAt: string;
  selected: boolean;
  eligible: boolean;
}

export interface CreateGiveawayRequest {
  title: string;
  description?: string;
  maxEntries?: number;
}

export interface GiveawayResultsResponse {
  giveawayId: string;
  title: string;
  status: GiveawayStatus;
  totalEntries: number;
  winnerCount: number;
  winners: Array<{
    entryId: string;
    participantName: string;
    enteredAt: string;
  }>;
  drawnAt: string | null;
}

export interface GiveawayStatsResponse {
  totalGiveaways: number;
  draftCount: number;
  openCount: number;
  closedCount: number;
  completedCount: number;
  cancelledCount: number;
  totalEntries: number;
  entriesPerGiveaway: Record<string, number>;
}
