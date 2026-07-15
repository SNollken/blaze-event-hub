import { useState } from 'react';
import { ArrowUpRight, Check, Copy, LifeBuoy, MessageCircle } from 'lucide-react';
import { useI18n } from '../i18n/I18nContext';

const DISCORD_HANDLE = '@snollken';

const steps = [
  ['01', 'helpStepConnectTitle', 'helpStepConnectText'],
  ['02', 'helpStepCreateTitle', 'helpStepCreateText'],
  ['03', 'helpStepCaptureTitle', 'helpStepCaptureText'],
  ['04', 'helpStepDrawTitle', 'helpStepDrawText'],
] as const;

const questions = [
  ['helpFaqNoEntriesQuestion', 'helpFaqNoEntriesAnswer'],
  ['helpFaqCreateQuestion', 'helpFaqCreateAnswer'],
  ['helpFaqDisconnectQuestion', 'helpFaqDisconnectAnswer'],
] as const;

export default function HelpSupport() {
  const { t } = useI18n();
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');

  const copyDiscordHandle = async () => {
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(DISCORD_HANDLE);
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  };

  return (
    <div className="hub-page support-page">
      <header className="support-hero">
        <div className="support-hero__index" aria-hidden="true">{t('helpIndex')}</div>
        <div className="support-hero__copy">
          <span className="page-eyebrow"><LifeBuoy size={15} aria-hidden="true" /> {t('helpEyebrow')}</span>
          <h1>{t('helpHeading')}</h1>
          <p>{t('helpSubtitle')}</p>
        </div>

        <aside className="support-ticket" aria-labelledby="support-contact-title">
          <span className="support-ticket__label">{t('helpContactLabel')}</span>
          <MessageCircle size={24} aria-hidden="true" />
          <h2 id="support-contact-title">{t('helpContactHeading')}</h2>
          <p>{t('helpContactDescription')}</p>
          <p className="support-ticket__safety">{t('helpContactSafety')}</p>
          <div className="support-ticket__handle">
            <span>{t('helpDiscordPlatform')}</span>
            <strong>{DISCORD_HANDLE}</strong>
          </div>
          <div className="support-ticket__actions">
            <button type="button" className="btn btn-primary" onClick={() => void copyDiscordHandle()}>
              {copyState === 'copied' ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}
              {copyState === 'copied' ? t('helpCopiedDiscord') : t('helpCopyDiscord')}
            </button>
            <a className="btn btn-secondary" href="https://discord.com/app" target="_blank" rel="noreferrer" aria-label={t('helpOpenDiscordAria')}>
              {t('helpOpenDiscord')} <ArrowUpRight size={16} aria-hidden="true" />
            </a>
          </div>
          <span className="sr-only" aria-live="polite">
            {copyState === 'copied' ? t('helpCopiedDiscord') : ''}
          </span>
          {copyState === 'failed' && <p className="support-ticket__error" role="alert">{t('helpCopyDiscordFailed')}</p>}
        </aside>
      </header>

      <section className="support-runbook" aria-labelledby="support-runbook-title">
        <div className="support-section-heading">
          <span>{t('helpRunbookLabel')}</span>
          <h2 id="support-runbook-title">{t('helpRunbookHeading')}</h2>
        </div>
        <ol className="support-steps">
          {steps.map(([number, titleKey, textKey]) => (
            <li key={number}>
              <span className="support-step__number" aria-hidden="true">{number}</span>
              <div>
                <h3>{t(titleKey)}</h3>
                <p>{t(textKey)}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="support-faq" aria-labelledby="support-faq-title">
        <div className="support-section-heading">
          <span>{t('helpFaqLabel')}</span>
          <h2 id="support-faq-title">{t('helpFaqHeading')}</h2>
        </div>
        <div className="support-faq__list">
          {questions.map(([questionKey, answerKey], index) => (
            <details key={questionKey} open={index === 0}>
              <summary>{t(questionKey)}</summary>
              <p>{t(answerKey)}</p>
            </details>
          ))}
        </div>
      </section>

      <footer className="support-credit">
        <a href="https://deerflow.tech" target="_blank" rel="noreferrer">{t('helpCredit')}</a>
      </footer>
    </div>
  );
}
