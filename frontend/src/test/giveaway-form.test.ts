import { describe, expect, it } from 'vitest';
import { defaultEntryCommand, normalizeXPostUrl } from '../utils/giveaway-form';

describe('giveaway form contract', () => {
  it('uses a localized default chat command', () => {
    expect(defaultEntryCommand('en')).toBe('!giveaway');
    expect(defaultEntryCommand('pt-BR')).toBe('!participar');
  });

  it('accepts HTTPS links to X posts and normalizes surrounding whitespace', () => {
    expect(normalizeXPostUrl('  https://x.com/sofia/status/123456789  '))
      .toBe('https://x.com/sofia/status/123456789');
    expect(normalizeXPostUrl('https://mobile.twitter.com/sofia/status/123456789?s=20'))
      .toBe('https://mobile.twitter.com/sofia/status/123456789?s=20');
    expect(normalizeXPostUrl('https://x.com/i/web/status/123456789'))
      .toBe('https://x.com/i/web/status/123456789');
  });

  it('rejects profiles, insecure URLs, credentials and lookalike domains', () => {
    expect(normalizeXPostUrl('https://x.com/sofia')).toBeNull();
    expect(normalizeXPostUrl('http://x.com/sofia/status/123456789')).toBeNull();
    expect(normalizeXPostUrl('https://user:pass@x.com/sofia/status/123456789')).toBeNull();
    expect(normalizeXPostUrl('https://x.com.example/sofia/status/123456789')).toBeNull();
  });
});
