import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

const json = (value: unknown) =>
  Promise.resolve(new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));

const now = '2026-06-29T12:00:00Z';

const responses: Record<string, unknown> = {
  '/api/status': {
    appName: 'NollenBlaze',
    version: '0.1.0',
    javaVersion: '21',
    blazeOAuthConfigured: true,
    blazeApiConfigured: true,
    socketConfigured: true,
    tokenPresent: true,
    refreshCredentialPresent: true,
    monitoredChannelConfigured: true,
    eventsRunning: false,
    sessionIdPresent: false,
    activeProfilesCount: 1,
    overlaysCount: 1,
    uptimeSeconds: 120,
    oauthConnected: true,
    profilePresent: true,
    connectedAccountDisplayName: 'Sofia',
    connectedAccountId: 'user-1',
    lastProfileSyncAt: now,
    nextRecommendedAction: null,
  },
  '/api/blaze/events/status': {
    runnerRunning: false,
    clientRunning: false,
    sessionId: null,
    lastMessageType: null,
    startedAt: null,
    engineAvailable: true,
    eventCount: 0,
  },
  '/api/blaze/oauth/session': {
    connected: true,
    tokenPresent: true,
    refreshCredentialPresent: true,
    profilePresent: true,
    profile: { id: 'user-1', username: 'sofia', displayName: 'Sofia', avatarUrl: null },
    tokenType: 'Bearer',
    userId: 'user-1',
    scopes: ['users.read'],
    expiresAt: now,
    tokenExpiredOrUnknown: false,
    lastConnectedAt: now,
    lastProfileSyncAt: now,
    nextRecommendedAction: null,
  },
  '/api/blaze/setup': {
    appName: 'NollenBlaze',
    environment: 'test',
    clientIdConfigured: true,
    clientIdMasked: 'abc...',
    clientCredentialConfigured: true,
    redirectUriConfigured: true,
    redirectUri: 'http://localhost:8080/api/blaze/oauth/callback',
    requestedScopes: ['users.read'],
    recommendedScopes: [],
    tokenPresent: true,
    tokenExpiredOrUnknown: false,
    refreshCredentialPresent: true,
    oauthConnected: true,
    profilePresent: true,
    connectedAccountDisplayName: 'Sofia',
    connectedAccountId: 'user-1',
    lastProfileSyncAt: now,
    nextRecommendedAction: null,
    monitoredChannelConfigured: true,
    monitoredChannel: 'canal',
    eventsConfigReady: true,
    oauthStartReady: true,
    checklist: [],
    missingItems: [],
    nextSteps: [],
    docsLinks: [],
    envExample: null,
  },
  '/api/alerts/rules': [],
  '/api/alerts/history': [],
  '/api/alerts/active': [],
  '/api/alerts/stats': {
    totalRules: 0,
    enabledRules: 0,
    totalAlerts: 0,
    unacknowledgedAlerts: 0,
    acknowledgedAlerts: 0,
    rules: [],
  },
  '/api/giveaways': [],
  '/api/giveaways/stats': {
    totalGiveaways: 0,
    draftCount: 0,
    openCount: 0,
    closedCount: 0,
    completedCount: 0,
    cancelledCount: 0,
    totalEntries: 0,
    entriesPerGiveaway: {},
  },
  '/api/overlay-profiles': [],
};

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    const path = url.startsWith('http') ? new URL(url).pathname : url.split('?')[0];
    return json(responses[path] ?? {});
  }));
  Object.assign(navigator, {
    clipboard: {
      writeText: vi.fn(),
    },
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
