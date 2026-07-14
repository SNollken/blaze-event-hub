import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

const now = '2026-07-14T12:00:00Z';

function response(value: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }));
}

const member = {
  id: 'member-1',
  blazeUserId: 'blaze-creator-1',
  blazeUsername: 'sofia',
  displayName: 'Sofia',
  avatarUrl: null,
  status: 'active',
};

const event = {
  id: 'event-1',
  creatorChannelId: 'channel-1',
  creatorChannelSlug: 'sofia',
  creatorChannelDisplayName: 'Sofia',
  creatorChannelAvatarUrl: null,
  title: 'Giveaway de teste',
  description: 'Evento usado pelos testes do fluxo principal.',
  prize: 'Gift card de R$ 100',
  entryCommand: '!participar',
  status: 'closed',
  finalizedParticipantCount: 1,
  finalizedPoolHash: 'a'.repeat(64),
  startsAt: null,
  endsAt: null,
  createdAt: now,
  updatedAt: now,
  openedAt: now,
  finalizationCutoffAt: now,
  closedAt: now,
  completedAt: null,
};

const stats = {
  eventId: 'event-1',
  status: 'closed',
  participantCount: 1,
  finalizedParticipantCount: 1,
  captureActive: false,
  captureHealth: 'INACTIVE',
  lastPolledAt: now,
  lastSuccessfulPollAt: now,
  lastErrorCode: null,
  canFinalize: false,
  canDraw: true,
  openedAt: now,
  closedAt: now,
  completedAt: null,
};

const participant = {
  blazeUserId: 'viewer-1',
  blazeUsername: 'viewer',
  displayName: 'Viewer One',
  enteredAt: now,
};

const drawResult = {
  eventId: 'event-1',
  winnerUsername: participant.blazeUsername,
  winnerDisplayName: participant.displayName,
  drawSeed: '123456',
  drawMethod: 'uniform_blaze_participants_v1',
  poolHash: event.finalizedPoolHash,
  participantCount: 1,
  selectedAt: now,
};

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input.toString();
    const url = new URL(raw, 'http://localhost');
    const path = url.pathname;

    if (path === '/api/blaze/oauth/session') {
      return response({
        connected: true,
        tokenPresent: true,
        profilePresent: true,
        profile: {
          id: member.blazeUserId,
          username: member.blazeUsername,
          displayName: member.displayName,
          avatarUrl: null,
        },
        scopes: ['users.read', 'offline.access'],
        nextRecommendedAction: null,
      });
    }
    if (path === '/api/blaze/oauth/start') return response({ authorizationUrl: 'https://example.com/oauth' });
    if (path === '/api/blaze/oauth/disconnect') return response({ disconnected: true, message: 'Conta desconectada.' });
    if (path === '/api/members/me') return response(member);
    if (path === '/api/blaze/channels/resolve') {
      return response({ id: 'channel-1', slug: url.searchParams.get('slug') || 'sofia', displayName: 'Canal da Sofia', avatarUrl: null });
    }
    if (path === '/api/events') return response([]);
    if (path === '/api/events/my/history') return response({ drafts: [], active: [], past: [] });
    if (path === '/api/events/event-1') return response(event);
    if (path === '/api/events/event-1/stats') return response(stats);
    if (path === '/api/events/event-1/participants') return response([participant]);
    if (path === '/api/events/event-1/draw' && options?.method === 'POST') return response(drawResult);
    if (path === '/api/events/event-1/winner') return response({ code: 'NOT_FOUND', message: 'Resultado ainda não existe.' }, 404);
    return response({});
  }));

  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });

  Object.assign(navigator, {
    clipboard: { writeText: vi.fn() },
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
