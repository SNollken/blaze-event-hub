import { Fragment } from 'react';
import { useI18n } from '../i18n/I18nContext';
import type { Lang } from '../i18n/translations';

const langs: { value: Lang; label: string }[] = [
  { value: 'pt-BR', label: 'PT' },
  { value: 'en', label: 'EN' },
];

export function LanguageSwitcher() {
  const { lang, setLang } = useI18n();

  return (
    <div className="lang-switcher">
      {langs.map((l) => (
        <Fragment key={l.value}>
          <input
            type="radio"
            name="lang"
            id={`lang-${l.value}`}
            value={l.value}
            checked={lang === l.value}
            onChange={() => setLang(l.value)}
            style={{ display: 'none' }}
          />
          <label htmlFor={`lang-${l.value}`}>
            {l.label}
          </label>
        </Fragment>
      ))}
      <div className="lang-glider" data-lang={lang === 'pt-BR' ? 'pt' : lang} />
    </div>
  );
}
