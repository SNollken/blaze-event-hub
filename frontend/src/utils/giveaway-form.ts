import type { Lang } from '../i18n/translations';

const X_POST_HOSTS = new Set([
  'x.com',
  'www.x.com',
  'mobile.x.com',
  'twitter.com',
  'www.twitter.com',
  'mobile.twitter.com',
]);
const X_POST_PATH = /^\/(?:[^/]+\/status|i\/web\/status)\/\d+(?:\/.*)?$/;

export function defaultEntryCommand(lang: Lang): string {
  return lang === 'pt-BR' ? '!participar' : '!giveaway';
}

export function normalizeXPostUrl(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;

  try {
    const url = new URL(trimmed);
    if (url.protocol !== 'https:' || url.username || url.password || url.port) return null;
    if (!X_POST_HOSTS.has(url.hostname.toLowerCase()) || !X_POST_PATH.test(url.pathname)) return null;
    return url.href;
  } catch {
    return null;
  }
}
