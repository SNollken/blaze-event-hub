import { translations, type Locale } from './translations';

/**
 * Minimal i18n: module-level locale + pure t() function.
 * No React context needed — components call t() directly.
 * setLocale() reloads the page so all components re-render with new strings.
 * ponytail: page reload on locale switch is acceptable for a streaming tool;
 * upgrade path: React context + I18nextProvider for hot-swap without reload.
 */

let currentLocale: Locale = 'pt-BR';

export function getLocale(): Locale {
  return currentLocale;
}

export function initI18n() {
  const saved = localStorage.getItem('i18n.locale');
  if (saved === 'en' || saved === 'pt-BR') {
    currentLocale = saved;
  } else {
    currentLocale = navigator.language.startsWith('pt') ? 'pt-BR' : 'en';
  }
}

export function setLocale(locale: Locale) {
  currentLocale = locale;
  localStorage.setItem('i18n.locale', locale);
  window.location.reload();
}

export function t(key: string): string {
  return translations[currentLocale]?.[key] ?? translations['pt-BR'][key] ?? translations['en'][key] ?? key;
}
