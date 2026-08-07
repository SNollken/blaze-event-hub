import { describe, expect, it, afterEach } from 'vitest';
import { t, setLocale, getLocale } from '../i18n';

describe('i18n', () => {
  afterEach(() => {
    // Reset locale — window.location.reload is a no-op in jsdom
    setLocale('pt-BR');
  });

  it('defaults to pt-BR', () => {
    expect(getLocale()).toBe('pt-BR');
  });

  it('returns Portuguese translation', () => {
    setLocale('pt-BR');
    expect(t('nav.dashboard')).toBe('Visão Geral');
    expect(t('common.yes')).toBe('Sim');
    expect(t('common.no')).toBe('Não');
  });

  it('returns English translation when locale is en', () => {
    setLocale('en');
    expect(t('nav.dashboard')).toBe('Overview');
    expect(t('common.yes')).toBe('Yes');
    expect(t('common.no')).toBe('No');
  });

  it('switches back to Portuguese', () => {
    setLocale('en');
    expect(t('nav.dashboard')).toBe('Overview');
    setLocale('pt-BR');
    expect(t('nav.dashboard')).toBe('Visão Geral');
  });

  it('falls back to key when translation missing', () => {
    setLocale('pt-BR');
    expect(t('nonexistent.key')).toBe('nonexistent.key');
  });

  it('translates unknownError key in both locales', () => {
    setLocale('pt-BR');
    expect(t('common.unknownError')).toBe('Erro desconhecido');
    setLocale('en');
    expect(t('common.unknownError')).toBe('Unknown error');
  });

  it('translates new keys (alerts status, uptime) in both locales', () => {
    setLocale('pt-BR');
    expect(t('alerts.statusActive')).toBe('Ativo');
    expect(t('alerts.statusInactive')).toBe('Inativo');
    expect(t('common.hour')).toBe('h');
    expect(t('common.minute')).toBe('min');
    setLocale('en');
    expect(t('alerts.statusActive')).toBe('Active');
    expect(t('alerts.statusInactive')).toBe('Inactive');
  });
});
