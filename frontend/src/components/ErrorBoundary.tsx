import { Component, ReactNode } from 'react';
import { t } from '../i18n';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  failed: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return (
        <div className="empty-state min-h-screen">
          <strong>{t('error.failed')}</strong>
          <span>{t('error.checkBackend')}</span>
          <button
            className="btn btn-primary"
            onClick={() => window.location.reload()}
            aria-label={t('error.reloadAria')}
          >
            {t('error.reload')}
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
