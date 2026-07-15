import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { translations, type Lang, type TranslationKey } from './translations';

const LANGUAGE_STORAGE_KEY = 'blaze-event-hub:language';

function isLang(value: string | null): value is Lang {
  return value === 'en' || value === 'pt-BR';
}

function readStoredLang(): Lang {
  try {
    const stored = window.localStorage.getItem(LANGUAGE_STORAGE_KEY);
    return isLang(stored) ? stored : 'en';
  } catch {
    return 'en';
  }
}

interface I18nValue {
  lang: Lang;
  setLang: (l: Lang) => void;
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nValue>({
  lang: 'en',
  setLang: () => {},
  t: (k) => k,
});

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(readStoredLang);

  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    try {
      window.localStorage.setItem(LANGUAGE_STORAGE_KEY, l);
    } catch {
      // A troca continua funcional durante a sessao se o storage estiver indisponivel.
    }
  }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const t = useCallback(
    (key: TranslationKey, params: Record<string, string | number> = {}): string => {
      const template = translations[lang]?.[key] || translations.en?.[key] || key;
      return template.replace(/\{(\w+)\}/g, (match, name: string) => (
        name in params ? String(params[name]) : match
      ));
    },
    [lang],
  );

  return (
    <I18nContext.Provider value={{ lang, setLang, t }}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}
