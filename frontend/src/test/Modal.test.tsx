import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Modal } from '../components/Modal';

/**
 * Caracterização do Modal (WCAG 2.2 AA): role/aria, foco inicial, focus trap
 * (Tab/Shift+Tab), Escape, click no overlay, restauração de foco no fechamento
 * e estabilidade de foco quando o pai re-renderiza com onClose inline (caso
 * real: forms controlados por estado da página re-renderizam a cada tecla).
 */

function renderOpen(onClose = vi.fn()) {
  return render(
    <Modal open onClose={onClose} title="Titulo do modal">
      <input aria-label="campo" />
      <button type="button">acao</button>
    </Modal>,
  );
}

describe('Modal', () => {
  it('nao renderiza nada quando fechado', () => {
    const { container } = render(
      <Modal open={false} onClose={vi.fn()} title="T">
        <p>conteudo</p>
      </Modal>,
    );
    expect(container.firstChild).toBeNull();
  });

  it('abre com role dialog, aria-modal e titulo acessivel', () => {
    renderOpen();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('aria-labelledby', 'modal-title');
    expect(screen.getByText('Titulo do modal')).toBeInTheDocument();
  });

  it('move o foco para dentro do modal ao abrir (primeiro focavel)', () => {
    renderOpen();
    // primeiro focavel em ordem DOM: botao Fechar do header
    expect(document.activeElement).toBe(screen.getByRole('button', { name: /fechar/i }));
  });

  it('Escape chama onClose', () => {
    const onClose = vi.fn();
    renderOpen(onClose);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('click no overlay fecha; click dentro do dialog nao fecha', () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="T">
        <p>conteudo</p>
      </Modal>,
    );
    fireEvent.click(screen.getByRole('dialog'));
    expect(onClose).not.toHaveBeenCalled();
    // overlay e o presentation que envolve o dialog
    fireEvent.click(document.querySelector('.modal-overlay') as HTMLElement);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('Tab no ultimo focavel volta para o primeiro (focus trap)', () => {
    render(
      <Modal open onClose={vi.fn()} title="T">
        <button type="button">ultimo</button>
      </Modal>,
    );
    const last = screen.getByRole('button', { name: 'ultimo' });
    last.focus();
    fireEvent.keyDown(window, { key: 'Tab' });
    expect(document.activeElement).toBe(screen.getByRole('button', { name: /fechar/i }));
  });

  it('Shift+Tab no primeiro focavel vai para o ultimo (focus trap)', () => {
    render(
      <Modal open onClose={vi.fn()} title="T">
        <button type="button">ultimo</button>
      </Modal>,
    );
    const close = screen.getByRole('button', { name: /fechar/i });
    close.focus();
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'ultimo' }));
  });

  it('ao fechar devolve o foco para o elemento que abriu o modal', () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <>
        <button type="button">gatilho</button>
        <Modal open onClose={onClose} title="T">
          <input aria-label="campo" />
        </Modal>
      </>,
    );
    const trigger = screen.getByRole('button', { name: 'gatilho' });
    // simula o fluxo real: o gatilho tinha foco quando o modal abriu
    trigger.focus();
    rerender(
      <>
        <button type="button">gatilho</button>
        <Modal open onClose={onClose} title="T">
          <input aria-label="campo" />
        </Modal>
      </>,
    );
    // fecha
    rerender(
      <>
        <button type="button">gatilho</button>
        <Modal open={false} onClose={onClose} title="T">
          <input aria-label="campo" />
        </Modal>
      </>,
    );
    expect(document.activeElement).toBe(trigger);
  });

  it('re-render do pai com onClose inline NAO rouba o foco do input (bug de form)', () => {
    // Cenario real: pages (Alerts/Dashboard/Giveaways/Overlays) passam
    // onClose={() => setState(false)} inline e o form dentro do modal e
    // controlado por estado da pagina -> cada tecla re-renderiza o pai.
    function Wrapper() {
      const [value, setValue] = useState('');
      const [open, setOpen] = useState(true);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>gatilho</button>
          <Modal open={open} onClose={() => setOpen(false)} title="T">
            <input aria-label="campo" value={value} onChange={(e) => setValue(e.target.value)} />
          </Modal>
        </>
      );
    }
    render(<Wrapper />);
    const input = screen.getByLabelText('campo') as HTMLInputElement;
    input.focus();
    fireEvent.change(input, { target: { value: 'a' } });
    expect(input.value).toBe('a');
    // foco deve permanecer no input apos o re-render do pai
    expect(document.activeElement).toBe(input);
  });

  it('apos fechar, Escape nao chama mais onClose (listener removido)', () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <Modal open onClose={onClose} title="T">
        <p>x</p>
      </Modal>,
    );
    rerender(
      <Modal open={false} onClose={onClose} title="T">
        <p>x</p>
      </Modal>,
    );
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });
});
