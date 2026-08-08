import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { Modal } from '../components/Modal';
import { Header } from '../components/Header';
import { DataTable } from '../components/DataTable';
import { ErrorBoundary } from '../components/ErrorBoundary';

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
    expect((await screen.findAllByText('Visão Geral')).length).toBeGreaterThan(0);
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

  it('exibe metricas de polling na pagina de eventos', async () => {
    renderRoute('/events');
    expect(await screen.findByText('Mensagens Vistas')).toBeInTheDocument();
    expect(await screen.findByText('Eventos Aceitos')).toBeInTheDocument();
    expect(await screen.findByText('Eventos Rejeitados')).toBeInTheDocument();
  });

  it('renderiza blaze channel', async () => {
    renderRoute('/blaze');
    expect((await screen.findAllByText('Blaze Channel')).length).toBeGreaterThan(0);
  });

  it('rota legada /channel redireciona para /blaze', async () => {
    renderRoute('/channel');
    expect((await screen.findAllByText('Blaze Channel')).length).toBeGreaterThan(0);
  });

  it('rota legada /live-events redireciona para /events', async () => {
    renderRoute('/live-events');
    expect(await screen.findByText('Mensagens Vistas')).toBeInTheDocument();
    expect(screen.queryByText('404')).not.toBeInTheDocument();
  });

  it('rota legada /alerts-dashboard redireciona para /alerts', async () => {
    renderRoute('/alerts-dashboard');
    expect(await screen.findByText('Nenhuma regra configurada.')).toBeInTheDocument();
    expect(screen.queryByText('404')).not.toBeInTheDocument();
  });

  it('rota legada /giveaways-dashboard redireciona para /giveaways', async () => {
    renderRoute('/giveaways-dashboard');
    expect(await screen.findByText('Nenhum sorteio criado.')).toBeInTheDocument();
    expect(screen.queryByText('404')).not.toBeInTheDocument();
  });

  it('rota legada /overlays-dashboard redireciona para /overlays', async () => {
    renderRoute('/overlays-dashboard');
    expect(await screen.findByText('Nenhum perfil criado.')).toBeInTheDocument();
    expect(screen.queryByText('404')).not.toBeInTheDocument();
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

  it('error boundary mostra botao de recarregar', () => {
    const Boom = () => {
      throw new Error('boom');
    };
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    const btn = screen.getByLabelText('Recarregar página');
    expect(btn).toBeInTheDocument();
    expect(() => btn.click()).not.toThrow();
  });
});
