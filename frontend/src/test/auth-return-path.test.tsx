import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RequireAuth from '../components/RequireAuth';
import { I18nProvider } from '../i18n/I18nContext';
import Login from '../pages/Login';

function json(value: unknown) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  }));
}

function LocationProbe() {
  const location = useLocation();
  return <span>{String((location.state as { from?: string } | null)?.from || 'sem-destino')}</span>;
}

function renderRoutes(initialEntry: string) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/login-probe" element={<LocationProbe />} />
          <Route path="/destino" element={<h1>Destino original</h1>} />
          <Route path="/protegida" element={<RequireAuth><h1>Área protegida</h1></RequireAuth>} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('retorno seguro depois do login', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(globalThis.fetch).mockReset();
  });

  it('preserva caminho, query e hash ao redirecionar uma rota protegida', async () => {
    vi.mocked(globalThis.fetch).mockImplementation(() => json({ connected: false }));

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/protegida?modo=edicao#regras']}>
          <Routes>
            <Route path="/login" element={<LocationProbe />} />
            <Route path="/protegida" element={<RequireAuth><h1>Área protegida</h1></RequireAuth>} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByText('/protegida?modo=edicao#regras')).toBeInTheDocument();
  });

  it('guarda o destino interno antes de sair para o OAuth', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/api/blaze/oauth/session')) return json({ connected: false });
      if (url.includes('/api/blaze/oauth/start')) return Promise.reject(new Error('interrompe redirecionamento no teste'));
      return json({});
    });

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/destino' } }]}>
          <Routes><Route path="/login" element={<Login />} /></Routes>
        </MemoryRouter>
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: /Conectar com Blaze/ }));
    await waitFor(() => expect(sessionStorage.getItem('beh_return_to')).toBe('/destino'));
  });

  it('retoma o destino salvo quando o callback confirma a conexão', async () => {
    sessionStorage.setItem('beh_return_to', '/destino');
    vi.mocked(globalThis.fetch).mockImplementation(() => json({ connected: true, profilePresent: false }));

    renderRoutes('/login?oauth=success');

    expect(await screen.findByRole('heading', { name: 'Destino original' })).toBeInTheDocument();
    expect(sessionStorage.getItem('beh_return_to')).toBeNull();
  });
});
