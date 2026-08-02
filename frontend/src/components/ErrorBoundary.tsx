import { Component, ReactNode } from 'react';

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
        <div className="empty-state" style={{ minHeight: '100vh' }}>
          <strong>Algo falhou ao carregar esta tela.</strong>
          <span>Verifique o backend e tente novamente.</span>
          <button
            className="btn btn-primary"
            onClick={() => window.location.reload()}
            aria-label="Recarregar pagina"
          >
            Recarregar
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
