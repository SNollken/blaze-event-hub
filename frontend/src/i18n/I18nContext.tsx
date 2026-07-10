import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { translations, type Lang, type TranslationKey } from './translations';

interface I18nValue {
  lang: Lang;
  setLang: (l: Lang) => void;
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nValue>({
  lang: 'pt-BR',
  setLang: () => {},
  t: (k) => k,
});

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => {
    const saved = localStorage.getItem('beh_lang');
    if (saved === 'en' || saved === 'pt-BR') return saved;
    return 'pt-BR';
  });

  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    localStorage.setItem('beh_lang', l);
  }, []);

  const t = useCallback(
    (key: TranslationKey, params: Record<string, string | number> = {}): string => {
      const template = translations[lang]?.[key] || translations['pt-BR']?.[key] || key;
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
