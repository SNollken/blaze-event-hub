import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { Layout } from '../components/Layout';
import { I18nProvider } from '../i18n/I18nContext';

function renderLayout() {
  return render(
    <I18nProvider>
      <MemoryRouter>
        <Layout><h1>Conteúdo principal</h1></Layout>
      </MemoryRouter>
    </I18nProvider>,
  );
}

function useMobileViewport() {
  vi.mocked(window.matchMedia).mockImplementation((query: string) => ({
    matches: query === '(max-width: 720px)',
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

describe('shell acessível', () => {
  it('oferece skip-link para o conteúdo principal', () => {
    renderLayout();

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' }))
      .toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
  });

  it('isola o conteúdo e mantém o foco dentro do drawer móvel', async () => {
    useMobileViewport();
    renderLayout();
    const trigger = screen.getByRole('button', { name: 'Abrir navegação' });
    const main = screen.getByRole('main');
    const sidebar = document.getElementById('sidebar');

    expect(sidebar).toHaveAttribute('aria-hidden', 'true');
    expect(sidebar).toHaveAttribute('inert');
    expect(screen.queryByRole('link', { name: /Blaze Event Hub/i })).not.toBeInTheDocument();

    fireEvent.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(sidebar).not.toHaveAttribute('aria-hidden');
    expect(sidebar).not.toHaveAttribute('inert');
    expect(main).toHaveAttribute('inert');
    expect(main).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('link', { name: /Blaze Event Hub/i })).toHaveFocus();

    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(trigger).toHaveAttribute('aria-expanded', 'false'));
    expect(sidebar).toHaveAttribute('aria-hidden', 'true');
    expect(sidebar).toHaveAttribute('inert');
    expect(main).not.toHaveAttribute('inert');
    expect(main).not.toHaveAttribute('aria-hidden');
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('ignora controles ocultos no trap e restaura foco ao fechar por X ou overlay', async () => {
    useMobileViewport();
    renderLayout();
    const trigger = screen.getByRole('button', { name: 'Abrir navegação' });
    const overlay = document.getElementById('sidebar-overlay');

    fireEvent.click(trigger);
    const logo = screen.getByRole('link', { name: /Blaze Event Hub/i });
    const profile = await screen.findByRole('button', { name: /Sofia/i });
    profile.focus();
    fireEvent.keyDown(window, { key: 'Tab' });
    expect(logo).toHaveFocus();

    fireEvent.click(screen.getByRole('button', { name: 'Fechar navegação' }));
    await waitFor(() => expect(trigger).toHaveFocus());

    fireEvent.click(trigger);
    fireEvent.click(overlay!);
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('fecha na mudança de rota e move o foco para o conteúdo', async () => {
    useMobileViewport();
    renderLayout();
    const trigger = screen.getByRole('button', { name: 'Abrir navegação' });
    const main = screen.getByRole('main');
    const sidebar = document.getElementById('sidebar');
    const pageContent = document.querySelector<HTMLElement>('.page-content')!;
    pageContent.scrollTop = 240;

    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole('link', { name: 'Meus giveaways' }));

    await waitFor(() => expect(trigger).toHaveAttribute('aria-expanded', 'false'));
    expect(sidebar).toHaveAttribute('aria-hidden', 'true');
    expect(sidebar).toHaveAttribute('inert');
    await waitFor(() => expect(main).toHaveFocus());
    expect(pageContent.scrollTop).toBe(0);
    expect(document.title).toBe('Meus giveaways | Blaze Event Hub');
  });

  it('mantém a navegação disponível no desktop quando o drawer está fechado', () => {
    renderLayout();
    const sidebar = document.getElementById('sidebar');

    expect(sidebar).not.toHaveAttribute('aria-hidden');
    expect(sidebar).not.toHaveAttribute('inert');
    expect(screen.getByRole('link', { name: /Blaze Event Hub/i })).toBeInTheDocument();
  });

  it('não exibe navegação legada de Studio', () => {
    renderLayout();

    expect(screen.getByRole('link', { name: 'Explorar' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Criar giveaway' })).toBeInTheDocument();
    expect(screen.queryByText('Studio')).not.toBeInTheDocument();
  });
});
