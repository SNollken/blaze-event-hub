import { Component, ReactNode } from 'react';
import { useI18n } from '../i18n/I18nContext';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  failed: boolean;
}

function ErrorFallback() {
  const { t } = useI18n();

  return (
    <div className="empty-state" style={{ minHeight: '100vh' }}>
      <strong>{t('errorBoundaryTitle')}</strong>
      <span>{t('errorBoundaryDescription')}</span>
    </div>
  );
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return <ErrorFallback />;
    }
    return this.props.children;
  }
}
