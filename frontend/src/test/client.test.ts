import { describe, expect, it, vi } from 'vitest';
import { getOAuthSession, getHealth } from '../api/client';

/* Tests the error-extraction logic in request() — verifies that
   ApiErrorResponse.message is extracted from JSON bodies, and that
   raw text / statusText are used as fallback. */
describe('API client error handling', () => {
	it('extracts message from JSON ApiErrorResponse', async () => {
		vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(
			JSON.stringify({
				timestamp: '2026-08-03T10:00:00Z',
				status: 401,
				code: 'TOKEN_EXPIRED',
				message: 'Token expired. Please refresh.',
				path: '/api/blaze/oauth/session',
			}),
			{ status: 401, headers: { 'Content-Type': 'application/json' } }
		))));

		await expect(getOAuthSession()).rejects.toThrow(
			'API 401: Token expired. Please refresh.'
		);
	});

	it('falls back to raw text for non-JSON errors', async () => {
		vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(
			'Internal Server Error',
			{ status: 500, headers: { 'Content-Type': 'text/plain' } }
		))));

		await expect(getHealth()).rejects.toThrow(
			'API 500: Internal Server Error'
		);
	});

	it('falls back to statusText when body is empty', async () => {
		vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('', { status: 404 }))));

		await expect(getHealth()).rejects.toThrow('API 404:');
	});

	it('handles JSON without message field by falling back to text', async () => {
		vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(
			JSON.stringify({ error: 'something went wrong' }),
			{ status: 500, headers: { 'Content-Type': 'application/json' } }
		))));

		const err = await getHealth().catch(e => e);
		expect(err.message).toContain('API 500:');
		expect(err.message).toContain('something went wrong');
	});
});
