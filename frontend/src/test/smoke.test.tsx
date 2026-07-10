import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { DataTable } from '../components/DataTable';
import { Header } from '../components/Header';
import { Modal } from '../components/Modal';
import { I18nProvider } from '../i18n/I18nContext';

function renderWithI18n(ui: ReactNode) {
  return render(<I18nProvider>{ui}</I18nProvider>);
}

function renderRoute(path: string) {
  return renderWithI18n(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('frontend smoke', () => {
  it('renderiza dashboard', async () => {
    renderRoute('/');
    expect(await screen.findByRole('heading', { name: 'Blaze Event Hub' })).toBeInTheDocument();
  });

  it('alterna o idioma globalmente entre PT e EN', async () => {
    renderRoute('/');
    expect(await screen.findByText('Eventos Abertos')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('EN'));
    expect(await screen.findByText('Open Events')).toBeInTheDocument();
    expect(screen.queryByLabelText('PH')).not.toBeInTheDocument();
  });

  it('renderiza eventos', async () => {
    renderRoute('/events');
    expect(await screen.findByRole('heading', { name: 'Eventos' })).toBeInTheDocument();
  });

  it('renderiza criação de evento', async () => {
    renderRoute('/events/create');
    expect(await screen.findByRole('heading', { name: 'Criar evento' })).toBeInTheDocument();
  });

  it('renderiza meus eventos', async () => {
    renderRoute('/my-events');
    expect(await screen.findByRole('heading', { name: 'Meus eventos' })).toBeInTheDocument();
  });

  it('renderiza login', async () => {
    renderRoute('/login');
    expect(await screen.findByRole('heading', { name: 'Blaze Event Hub' })).toBeInTheDocument();
  });

  it('renderiza sorteio ao vivo com dados da API', async () => {
    renderRoute('/events/event-1/draw');
    expect(await screen.findByRole('heading', { name: 'Sorteio ao vivo' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Iniciar sorteio' })).toBeInTheDocument();
  });

  it('modal fecha com Escape', async () => {
    const onClose = vi.fn();
    renderWithI18n(
      <Modal open onClose={onClose} title="Teste">
        Conteúdo
      </Modal>,
    );
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
  });

  it('header tem botão acessível de atualizar', async () => {
    renderWithI18n(<Header title="Título" />);
    expect(await screen.findByLabelText('Atualizar status')).toBeInTheDocument();
  });

  it('datatable mostra estado vazio', () => {
    renderWithI18n(<DataTable columns={[{ key: 'name', header: 'Nome' }]} data={[]} emptyMessage="Nada aqui." />);
    expect(screen.getByText('Nada aqui.')).toBeInTheDocument();
  });
});
