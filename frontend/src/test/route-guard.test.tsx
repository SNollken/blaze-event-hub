import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import App from '../App';
import { I18nProvider } from '../i18n/I18nContext';

function renderRoute(path: string) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('route guard', () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
    vi.restoreAllMocks();
  });

  it('redireciona /events/create para /login quando nao logado', async () => {
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.includes('/api/blaze/oauth/session')) {
        return new Response(JSON.stringify({ connected: false }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }
      return new Response('{}', { status: 200 });
    }) as typeof fetch;

    renderRoute('/events/create');
    // Deve aparecer o heading do Login (tela de destino do redirect)
    expect(await screen.findByRole('heading', { name: 'Blaze Event Hub' })).toBeInTheDocument();
    // E NAO o heading de criar evento
    expect(screen.queryByRole('heading', { name: 'Criar evento' })).not.toBeInTheDocument();
  });
});
