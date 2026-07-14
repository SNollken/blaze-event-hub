import { describe, expect, it } from 'vitest';
import { toSafeOAuthUrl } from '../oauth-navigation';

describe('navegação OAuth segura', () => {
  it('aceita uma autorização HTTPS', () => {
    expect(toSafeOAuthUrl('https://blaze.stream/oauth/authorize')).toBe(
      'https://blaze.stream/oauth/authorize',
    );
  });

  it('aceita uma rota local para desenvolvimento', () => {
    expect(toSafeOAuthUrl('/oauth/local')).toBe(`${window.location.origin}/oauth/local`);
  });

  it.each([
    'javascript:alert(1)',
    'data:text/html,unsafe',
    'http://example.com/oauth',
  ])('rejeita destino inseguro: %s', (unsafeUrl) => {
    expect(() => toSafeOAuthUrl(unsafeUrl)).toThrow(/não é seguro/i);
  });
});
