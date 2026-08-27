import { act, render, screen, waitFor } from '@testing-library/react';
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

  it('exibe metricas de polling na pagina de eventos', async () => {
    renderRoute('/events');
    expect(await screen.findByRole('button', { name: 'Iniciar eventos' })).toBeInTheDocument();
    expect(screen.queryByText('Eventos Aceitos')).not.toBeInTheDocument();
    expect(screen.queryByText('Eventos Rejeitados')).not.toBeInTheDocument();
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

  it('Toast real deduplica erros identicos do BlazeChannel + AccountFooter (end-to-end, sem mock do Toast)', async () => {
    // Bug r60: a pagina BlazeChannel e o AccountFooter da Sidebar mantem
    // usePolling independentes do mesmo getStatus, cada um com seu
    // hasErroredRef, e ambos disparam addToast('error', ...) na primeira
    // falha. Sem o dedup do Toast dois toasts identicos apareciam.
    // Aqui SO /api/status falha; os demais endpoints seguem o mock do
    // setup, entao exatamente dois addToast identicos sao disparados.
    const originalFetch = window.fetch;
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const path = url.startsWith('http') ? new URL(url).pathname : url.split('?')[0];
      if (path === '/api/status') {
        return Promise.resolve(new Response('nope', { status: 500 }));
      }
      return (originalFetch as (i: RequestInfo | URL) => Promise<Response>)(input);
    }));

    await act(async () => {
      renderRoute('/blaze');
    });
    // flusha a onda inicial de fetch dos dois pollers de getStatus
    await act(async () => {});
    await act(async () => {});

    await screen.findAllByText('API 500: nope');
    // Um unico toast visivel apesar dos dois disparos identicos
    expect(document.querySelectorAll('.toast')).toHaveLength(1);
  });
});
