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
    appName: 'Blaze Event Hub',
    version: '0.1.0',
    javaVersion: '21',
    blazeOAuthConfigured: true,
    eventsRunning: false,
    sessionIdPresent: false,
    oauthConnected: true,
    profilePresent: true,
    connectedAccountDisplayName: 'Sofia',
    uptimeSeconds: 120,
    nextRecommendedAction: null,
  },
  '/api/blaze/oauth/session': {
    connected: true,
    tokenPresent: true,
    profilePresent: true,
    scopes: ['users.read'],
    nextRecommendedAction: null,
  },
  '/api/blaze/oauth/start': {
    authorizationUrl: 'https://example.com/auth',
  },
  '/api/members/me': {
    id: 'user-1',
    blazeUserId: 'blaze-1',
    blazeUsername: 'sofia',
    displayName: 'Sofia',
    avatarUrl: null,
    status: 'ACTIVE',
  },
  '/api/events': [],
  '/api/events/my/history': {
    drafts: [],
    upcoming: [],
    past: [],
  },
  '/api/events/event-1': {
    id: 'event-1',
    creatorMemberId: 'user-1',
    creatorChannelId: 'blaze-1',
    title: 'Sorteio de teste',
    description: 'Evento usado pelo smoke test.',
    status: 'CLOSED',
    rulesMode: 'tier',
    maxEntriesPerParticipant: 0,
    requiresInterestBeforeAction: false,
    startsAt: null,
    endsAt: null,
    rules: [],
  },
  '/api/events/event-1/stats': {
    totalVotes: 10,
    totalSubs: 1,
    totalGiftedSubs: 0,
    participants: 1,
    totalEntries: 10,
    last24h: {
      votes: 10,
      subs: 1,
      giftedSubs: 0,
    },
  },
  '/api/events/event-1/interest/participants': [
    {
      memberId: 'user-1',
      blazeUsername: 'sofia',
      displayName: 'Sofia',
      status: 'ACTIVE',
      lastCalculatedEntries: 10,
    },
  ],
};

beforeEach(() => {
  localStorage.clear();
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
