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
    expect((await screen.findAllByText('Início')).length).toBeGreaterThan(0);
  });

  it('mostra cards do dashboard', async () => {
    renderRoute('/');
    expect(await screen.findByText('Sorteios abertos')).toBeInTheDocument();
    expect(await screen.findByText('Sorteios recentes')).toBeInTheDocument();
    // mock /api/status: oauthConnected=true, connectedAccountDisplayName='Sofia'
    expect(await screen.findByText('Sofia')).toBeInTheDocument();
  });

  it('renderiza live events', async () => {
    renderRoute('/events');
    expect((await screen.findAllByText('Eventos ao Vivo')).length).toBeGreaterThan(0);
  });

  it('nao oferece mais a pagina Blaze Channel', async () => {
    renderRoute('/blaze');
    expect(await screen.findByText('404')).toBeInTheDocument();
  });

  it('exibe metricas de polling na pagina de eventos', async () => {
    renderRoute('/events');
    expect(await screen.findByRole('button', { name: 'Iniciar eventos' })).toBeInTheDocument();
    expect(screen.queryByText('Eventos Aceitos')).not.toBeInTheDocument();
    expect(screen.queryByText('Eventos Rejeitados')).not.toBeInTheDocument();
  });

  it('rota legada /live-events redireciona para /events', async () => {
    renderRoute('/live-events');
    expect(await screen.findByRole('button', { name: 'Iniciar eventos' })).toBeInTheDocument();
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

  it('rota legada /dashboard redireciona para / (home)', async () => {
    renderRoute('/dashboard');
    expect(await screen.findByText('Sorteios abertos')).toBeInTheDocument();
    expect(screen.queryByText('404')).not.toBeInTheDocument();
  });

  it('rota desconhecida mostra 404', async () => {
    renderRoute('/rota-que-nao-existe');
    expect(await screen.findByText('404')).toBeInTheDocument();
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

  it('header tem botao acessivel de idioma', async () => {
    render(
      <MemoryRouter>
        <Header title="Titulo" />
      </MemoryRouter>
    );
    expect(await screen.findByLabelText('Alternar para inglês')).toBeInTheDocument();
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
