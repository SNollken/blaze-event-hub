import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { addToast, ToastContainer } from '../components/Toast';

/**
 * Toast usa pub/sub em estado de modulo (listeners/toasts globais) e
 * auto-dismiss via setTimeout(4000). Testes drenam o estado global no fim
 * (avancando timers ate o auto-dismiss) para nao vazar entre testes.
 */
describe('Toast', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('container tem role log com aria-live polite (anuncio sem roubar foco)', () => {
    render(<ToastContainer />);
    const log = screen.getByRole('log');
    expect(log).toHaveAttribute('aria-live', 'polite');
  });

  it('addToast exibe o texto com a classe do tipo', () => {
    vi.useFakeTimers();
    render(<ToastContainer />);
    act(() => addToast('success', 'Salvo com sucesso'));
    const item = screen.getByText('Salvo com sucesso').closest('.toast');
    expect(item).not.toBeNull();
    expect(item).toHaveClass('toast-success');
    act(() => vi.advanceTimersByTime(4000)); // drena o auto-dismiss
    expect(screen.queryByText('Salvo com sucesso')).not.toBeInTheDocument();
  });

  it('multiplos toasts empilham e cada um tem botao de fechar acessivel', () => {
    vi.useFakeTimers();
    render(<ToastContainer />);
    act(() => {
      addToast('error', 'Falha A');
      addToast('warning', 'Atencao B');
    });
    expect(screen.getByText('Falha A')).toBeInTheDocument();
    expect(screen.getByText('Atencao B')).toBeInTheDocument();
    const closeButtons = screen.getAllByRole('button', { name: 'Fechar notificação' });
    expect(closeButtons).toHaveLength(2);
    act(() => vi.advanceTimersByTime(4000));
  });

  it('click no botao de fechar remove o toast imediatamente', () => {
    vi.useFakeTimers();
    render(<ToastContainer />);
    act(() => addToast('error', 'Erro removivel'));
    fireEvent.click(screen.getByRole('button', { name: 'Fechar notificação' }));
    expect(screen.queryByText('Erro removivel')).not.toBeInTheDocument();
  });

  it('auto-dismiss apos 4s (sem intervencao)', () => {
    vi.useFakeTimers();
    render(<ToastContainer />);
    act(() => addToast('warning', 'Temporario'));
    expect(screen.getByText('Temporario')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(3999));
    expect(screen.getByText('Temporario')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1));
    expect(screen.queryByText('Temporario')).not.toBeInTheDocument();
  });

  it('unmount do container remove o listener (addToast posterior nao quebra)', () => {
    vi.useFakeTimers();
    const { unmount } = render(<ToastContainer />);
    unmount();
    expect(() => act(() => addToast('success', 'Orfao'))).not.toThrow();
    act(() => vi.advanceTimersByTime(4000)); // drena o timer do auto-dismiss
  });
});
