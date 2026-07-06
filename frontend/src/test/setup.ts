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
    uptimeSeconds: 120,
  },
  '/api/events': [],
  '/api/members/me': {
    id: 'user-1',
    username: 'sofia',
    displayName: 'Sofia',
    avatarUrl: null,
  },
  '/api/audit': [],
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
