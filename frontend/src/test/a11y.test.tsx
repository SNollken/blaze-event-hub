import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { DataTable } from '../components/DataTable';
import { ErrorBanner } from '../components/ErrorBanner';
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
    addToast('success', 'Sorteio criado com sucesso');
    expect(await screen.findByText('Sorteio criado com sucesso')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
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
    await screen.findByText('Mensagens Vistas');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pagina Blaze Channel nao tem violacoes', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/blaze']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findAllByText('Blaze Channel');
    expect(await axe(container)).toHaveNoViolations();
  });
});
