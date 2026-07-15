import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState, type ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { Layout } from '../components/Layout';
import { Modal } from '../components/Modal';
import { Sidebar } from '../components/Sidebar';
import { addToast } from '../components/Toast';
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
  beforeEach(() => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
  });

  it('renderiza dashboard', async () => {
    renderRoute('/');
    expect(await screen.findByRole('heading', { name: 'Blaze Event Hub' })).toBeInTheDocument();
  });

  it('renderiza eventos', async () => {
    renderRoute('/events');
    expect(await screen.findByRole('heading', { name: 'Eventos' })).toBeInTheDocument();
  });

  it('renderiza criação de evento', async () => {
    renderRoute('/events/create');
    expect(await screen.findByRole('heading', { name: 'Prepare a entrada antes de entrar ao vivo' })).toBeInTheDocument();
  });

  it('renderiza meus eventos', async () => {
    renderRoute('/my-events');
    expect(await screen.findByRole('heading', { name: 'Meus eventos' })).toBeInTheDocument();
  });

  it('renderiza login', async () => {
    renderRoute('/login');
    expect(await screen.findByRole('heading', { name: 'Conecte sua conta Blaze' })).toBeInTheDocument();
  });

  it('renderiza sorteio ao vivo com dados da API', async () => {
    renderRoute('/events/event-1/draw');
    expect(await screen.findByRole('heading', { name: 'Sorteio ao vivo' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Iniciar sorteio' })).toBeInTheDocument();
  });

  it('modal gerencia foco e fecha com Escape', async () => {
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>Abrir modal</button>
          <Modal open={open} onClose={() => setOpen(false)} title="Teste">
            Conteúdo
          </Modal>
        </>
      );
    }

    renderWithI18n(<Harness />);
    const opener = screen.getByRole('button', { name: 'Abrir modal' });
    opener.focus();
    fireEvent.click(opener);

    const closeButton = await screen.findByRole('button', { name: 'Fechar' });
    await waitFor(() => expect(closeButton).toHaveFocus());

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(opener).toHaveFocus();
  });

  it('drawer movel anuncia o estado e fecha com Escape', async () => {
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
    renderWithI18n(
      <MemoryRouter>
        <Layout>Conteudo</Layout>
      </MemoryRouter>,
    );

    const menu = screen.getByRole('button', { name: 'Abrir navegação' });
    expect(menu).toHaveAttribute('aria-controls', 'sidebar');
    expect(menu).toHaveAttribute('aria-expanded', 'false');

    fireEvent.click(menu);
    expect(menu).toHaveAttribute('aria-expanded', 'true');
    expect(document.body.style.overflow).toBe('hidden');

    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(menu).toHaveAttribute('aria-expanded', 'false'));
    expect(document.body.style.overflow).toBe('');
    expect(screen.getByRole('main')).toHaveTextContent('Conteudo');
  });

  it('logout desconecta no backend e atualiza a sidebar imediatamente', async () => {
    let connected = true;
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = String(input);
      if (path === '/api/blaze/oauth/session') {
        return Promise.resolve(new Response(JSON.stringify({
          connected,
          tokenPresent: connected,
          profilePresent: connected,
          profile: connected ? { id: '1', username: 'sofia', displayName: 'Sofia', avatarUrl: null } : null,
          scopes: [],
          nextRecommendedAction: null,
        }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
      }
      if (path === '/api/members/me') {
        return Promise.resolve(new Response(JSON.stringify({ displayName: 'Sofia', avatarUrl: null }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      if (path === '/api/blaze/oauth/disconnect') {
        connected = false;
        return Promise.resolve(new Response(JSON.stringify({ disconnected: true, message: 'Conta desconectada.' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return Promise.resolve(new Response('{}', { status: 404 }));
    });

    renderWithI18n(
      <MemoryRouter>
        <Sidebar open onClose={vi.fn()} />
      </MemoryRouter>,
    );

    fireEvent.click(await screen.findByRole('button', { name: /Sofia/ }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Sair da conta' }));

    await waitFor(() => {
      const disconnectCall = vi.mocked(globalThis.fetch).mock.calls.find(([input]) => (
        String(input) === '/api/blaze/oauth/disconnect'
      ));
      expect(disconnectCall).toBeDefined();
      expect(disconnectCall?.[1]?.method).toBe('POST');
    });
    expect(await screen.findByRole('link', { name: 'Conectar Blaze' })).toBeInTheDocument();
  });

  it('exibe feedback global disparado por uma pagina', async () => {
    renderWithI18n(
      <MemoryRouter>
        <Layout>Conteudo</Layout>
      </MemoryRouter>,
    );

    addToast('success', 'Operacao concluida');

    expect(await screen.findByText('Operacao concluida')).toBeInTheDocument();
  });
});
