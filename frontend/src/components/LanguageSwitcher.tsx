import { useId, type ChangeEvent } from 'react';
import { useI18n } from '../i18n/I18nContext';
import type { Lang } from '../i18n/translations';

export function LanguageSwitcher() {
  const { lang, setLang, t } = useI18n();
  const selectId = useId();

  const handleChange = (event: ChangeEvent<HTMLSelectElement>) => {
    setLang(event.currentTarget.value as Lang);
  };

  return (
    <div className="language-switcher">
      <label htmlFor={selectId}>{t('languageLabel')}</label>
      <select id={selectId} value={lang} onChange={handleChange}>
        <option value="en">{t('languageEnglish')}</option>
        <option value="pt-BR">{t('languagePortugueseBrazil')}</option>
      </select>
    </div>
  );
}
