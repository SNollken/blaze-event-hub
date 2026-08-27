import { type ReactNode } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { DataTable } from '../components/DataTable';
import { ErrorBanner } from '../components/ErrorBanner';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { Header } from '../components/Header';
import { Modal } from '../components/Modal';
import { StatsCard } from '../components/StatsCard';
import { addToast, ToastContainer } from '../components/Toast';
import { axe } from './axe';

/**
 * WCAG 2.2 AA regression guard: axe-core over shared components and the
 * page shells. color-contrast is excluded (jsdom has no layout; see axe.ts).
 */
describe('a11y (axe-core)', () => {
  it('ErrorBanner com retry nao tem violacoes', async () => {
    const { container } = render(
      <ErrorBanner error="Falha de conexao com a API" onRetry={() => {}} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Modal aberto nao tem violacoes', async () => {
    const { container } = render(
      <Modal
        open
        onClose={() => {}}
        title="Criar sorteio"
        footer={
          <button className="btn btn-primary" onClick={() => {}}>
            Salvar
          </button>
        }
      >
        <label htmlFor="a11y-modal-field">Nome do sorteio</label>
        <input id="a11y-modal-field" className="input" />
      </Modal>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('StatsCard nao tem violacoes', async () => {
    const { container } = render(
      <StatsCard title="Sorteios abertos" value={3} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Header nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter>
        <Header title="Teste de pagina" />
      </MemoryRouter>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('DataTable com filtro e ordenacao nao tem violacoes', async () => {
    const { container } = render(
      <DataTable
        columns={[
          { key: 'name', header: 'Nome', sortable: true },
          { key: 'entries', header: 'Inscricoes' },
        ]}
        data={[
          { name: 'Sofia', entries: 3 },
          { name: 'Alice', entries: 1 },
        ]}
        filterable
        ariaLabel="Participantes do sorteio"
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('ToastContainer com mensagem nao tem violacoes', async () => {
    const { container } = render(<ToastContainer />);
    act(() => addToast('success', 'Sorteio criado com sucesso'));
    expect(await screen.findByText('Sorteio criado com sucesso')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('ErrorBoundary em estado de erro nao tem violacoes', async () => {
    function Boom(): ReactNode {
      throw new Error('boom controlado para teste de a11y');
    }
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const { container } = render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    await screen.findByText('Algo deu errado ao carregar esta tela.');
    expect(await axe(container)).toHaveNoViolations();
    errorSpy.mockRestore();
  });

  it('pagina Home nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findAllByText('Início');
    await screen.findByText('Sorteios abertos');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pagina Sorteios nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/giveaways']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('Nenhum sorteio criado.');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pagina Alertas nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/alerts']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('Nenhuma regra configurada.');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pagina Overlays nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/overlays']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('Nenhum perfil criado.');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pagina Eventos ao Vivo nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/events']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByRole('button', { name: 'Iniciar eventos' });
    expect(await axe(container)).toHaveNoViolations();
  });

  it('modal da roleta aberto na pagina Sorteios nao tem violacoes', async () => {
    const closedGiveaway = {
      id: 'g1', title: 'Sorteio da Roleta', description: '', status: 'CLOSED',
      entryCount: 3, maxEntries: 100, createdAt: '2026-08-13T00:00:00Z',
      openedAt: '2026-08-13T00:01:00Z', closedAt: '2026-08-13T00:02:00Z',
      drawnAt: null, winnerIds: [],
    };
    const wheelEntries = [
      { id: 'e1', giveawayId: 'g1', participantName: 'alice', enteredAt: '2026-08-13T00:00:00Z', selected: false, eligible: true },
      { id: 'e2', giveawayId: 'g1', participantName: 'bob', enteredAt: '2026-08-13T00:00:01Z', selected: false, eligible: true },
      { id: 'e3', giveawayId: 'g1', participantName: 'carol', enteredAt: '2026-08-13T00:00:02Z', selected: false, eligible: true },
    ];
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const path = url.startsWith('http') ? new URL(url).pathname : url.split('?')[0];
      const body =
        path === '/api/giveaways' ? [closedGiveaway]
        : path === '/api/giveaways/stats' ? {
            totalGiveaways: 1, draftCount: 0, openCount: 0, closedCount: 1,
            completedCount: 0, cancelledCount: 0, totalEntries: 3,
            entriesPerGiveaway: { g1: 3 },
          }
        : path === '/api/giveaways/g1/entries' ? wheelEntries
        : {};
      return Promise.resolve(new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }));
    }));

    const { container } = render(
      <MemoryRouter initialEntries={['/giveaways']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('Sorteio da Roleta');
    fireEvent.click(screen.getByLabelText('Sortear vencedor com roleta Sorteio da Roleta'));
    await screen.findByText('Girar');
    expect(await axe(container)).toHaveNoViolations();
  });
});
