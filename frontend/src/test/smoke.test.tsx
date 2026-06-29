import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { Modal } from '../components/Modal';
import { Header } from '../components/Header';
import { DataTable } from '../components/DataTable';

function renderRoute(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('frontend smoke', () => {
  it('renderiza dashboard', async () => {
    renderRoute('/');
    expect((await screen.findAllByText('Visao Geral')).length).toBeGreaterThan(0);
  });

  it('mostra cards do dashboard', async () => {
    renderRoute('/');
    expect(await screen.findByText('Backend')).toBeInTheDocument();
    expect((await screen.findAllByText('Blaze OAuth')).length).toBeGreaterThan(0);
  });

  it('renderiza live events', async () => {
    renderRoute('/events');
    expect((await screen.findAllByText('Eventos ao Vivo')).length).toBeGreaterThan(0);
  });

  it('renderiza blaze channel', async () => {
    renderRoute('/blaze');
    expect((await screen.findAllByText('Blaze Channel')).length).toBeGreaterThan(0);
  });

  it('renderiza alerts conectado a API', async () => {
    renderRoute('/alerts');
    expect((await screen.findAllByText('Alertas')).length).toBeGreaterThan(0);
    expect(await screen.findByText('Nenhuma regra configurada.')).toBeInTheDocument();
  });

  it('renderiza giveaways conectado a API', async () => {
    renderRoute('/giveaways');
    expect((await screen.findAllByText('Sorteios')).length).toBeGreaterThan(0);
    expect(await screen.findByText('Nenhum sorteio criado.')).toBeInTheDocument();
  });

  it('renderiza overlays', async () => {
    renderRoute('/overlays');
    expect((await screen.findAllByText('Overlays')).length).toBeGreaterThan(0);
  });

  it('modal fecha com Escape', async () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="Teste">
        Conteudo
      </Modal>,
    );
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
  });

  it('header tem botao acessivel de atualizar', async () => {
    render(
      <MemoryRouter>
        <Header title="Titulo" />
      </MemoryRouter>,
    );
    expect(await screen.findByLabelText('Atualizar status')).toBeInTheDocument();
  });

  it('datatable mostra estado vazio', () => {
    render(<DataTable columns={[{ key: 'name', header: 'Nome' }]} data={[]} emptyMessage="Nada aqui." />);
    expect(screen.getByText('Nada aqui.')).toBeInTheDocument();
  });
});
