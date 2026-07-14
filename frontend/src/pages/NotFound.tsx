import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nContext';

export default function NotFound() {
  const { t } = useI18n();

  return (
    <div className="page not-found">
      <span className="not-found-code">404</span>
      <h1 className="page-title">{t('notFoundTitle')}</h1>
      <p className="page-subtitle">{t('notFoundDescription')}</p>
      <Link to="/" className="btn btn-primary">
        <ArrowLeft size={15} aria-hidden="true" /> {t('notFoundBack')}
      </Link>
    </div>
  );
}
