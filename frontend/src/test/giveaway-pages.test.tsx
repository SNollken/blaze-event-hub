import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nContext';
import EventDetail from '../pages/EventDetail';
import EventResult from '../pages/EventResult';
import LiveDraw from '../pages/LiveDraw';

const event = {
  id: 'event-1',
  creatorChannelId: 'channel-1',
  creatorChannelSlug: 'sofia',
  creatorChannelDisplayName: 'Sofia',
  creatorChannelAvatarUrl: null,
  title: 'Giveaway de lançamento',
  description: 'Uma celebração com a comunidade.',
  prize: 'Teclado mecânico',
  xPostUrl: 'https://x.com/sofia/status/123456789',
  entryCommand: '!participar',
  status: 'CLOSED',
  finalizedParticipantCount: 2,
  finalizedPoolHash: 'pool-hash-123',
  startsAt: null,
  endsAt: null,
  createdAt: '2026-07-14T12:00:00Z',
  updatedAt: '2026-07-14T13:00:00Z',
  openedAt: '2026-07-14T12:10:00Z',
  finalizationCutoffAt: '2026-07-14T13:00:00Z',
  closedAt: '2026-07-14T13:00:00Z',
  completedAt: null,
};

const stats = {
  eventId: 'event-1',
  status: 'CLOSED',
  participantCount: 2,
  finalizedParticipantCount: 2,
  captureActive: false,
  captureHealth: 'INACTIVE',
  lastPolledAt: event.closedAt,
  lastSuccessfulPollAt: event.closedAt,
  lastErrorCode: null,
  canFinalize: false,
  canDraw: true,
  openedAt: event.openedAt,
  closedAt: event.closedAt,
  completedAt: null,
};

const result = {
  eventId: 'event-1',
  winnerUsername: 'bia',
  winnerDisplayName: 'Bia',
  drawSeed: 'seed-456',
  drawMethod: 'uniform_blaze_participants_v1',
  poolHash: 'pool-hash-123',
  participantCount: 2,
  selectedAt: '2026-07-14T13:05:00Z',
};

function json(value: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }));
}

function mockApi(completed = false) {
  vi.mocked(globalThis.fetch).mockImplementation((input, options) => {
    const path = String(input).split('?')[0];
    if (path === '/api/events/event-1') {
      return json(completed ? { ...event, status: 'COMPLETED', completedAt: result.selectedAt } : event);
    }
    if (path === '/api/events/event-1/stats') return json(stats);
    if (path === '/api/events/event-1/participants') {
      return json([
        { blazeUserId: 'blaze-1', blazeUsername: 'ana', displayName: 'Ana', enteredAt: event.openedAt },
        { blazeUserId: 'blaze-2', blazeUsername: 'bia', displayName: 'Bia', enteredAt: event.openedAt },
      ]);
    }
    if (path === '/api/events/event-1/winner') return json(result);
    if (path === '/api/events/event-1/draw' && options?.method === 'POST') return json(result);
    if (path === '/api/blaze/oauth/session') return json({ connected: true, profilePresent: true });
    if (path === '/api/members/me') return json({ id: 'creator-1', displayName: 'Sofia' });
    return json({}, 404);
  });
}

function renderAt(path: string, element: React.ReactNode) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path={path.replace('event-1', ':id')} element={element} /></Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('páginas do giveaway', () => {
  beforeEach(() => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
  });

  it('mostra o contrato público sem participação manual no site', async () => {
    mockApi();
    renderAt('/events/event-1', <EventDetail />);

    expect(await screen.findByRole('heading', { name: 'Giveaway de lançamento' })).toBeInTheDocument();
    expect(screen.getByText('Teclado mecânico')).toBeInTheDocument();
    expect(screen.getByText('!participar')).toBeInTheDocument();
    expect(screen.getByText('2 participantes')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Abrir transmissão/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /@sofia/i }))
      .toHaveAttribute('href', 'https://blaze.stream/sofia');
    expect(screen.getByRole('link', { name: 'Ver o post original no X' }))
      .toHaveAttribute('href', 'https://x.com/sofia/status/123456789');
    expect(screen.getByRole('heading', { name: 'Comando usado durante a captura' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /participar/i })).not.toBeInTheDocument();
  });

  it('não converte falha das métricas em zero participantes', async () => {
    mockApi();
    const currentFetch = vi.mocked(globalThis.fetch).getMockImplementation();
    vi.mocked(globalThis.fetch).mockImplementation((input, options) => {
      if (String(input).split('?')[0] === '/api/events/event-1/stats') {
        return json({ message: 'Métricas indisponíveis' }, 503);
      }
      return currentFetch!(input, options);
    });

    renderAt('/events/event-1', <EventDetail />);

    expect(await screen.findByText('Contagem indisponível')).toBeInTheDocument();
    expect(screen.queryByText('0 participantes')).not.toBeInTheDocument();
  });

  it('publica vencedor e o registro técnico do resultado', async () => {
    mockApi(true);
    renderAt('/events/event-1/result', <EventResult />);

    expect(await screen.findByRole('heading', { name: 'Bia' })).toBeInTheDocument();
    expect(screen.getByText('seed-456')).toBeInTheDocument();
    expect(screen.getByText('pool-hash-123')).toBeInTheDocument();
    expect(screen.getByText('uniform_blaze_participants_v1')).toBeInTheDocument();
    expect(screen.queryByText(/blaze-2/)).not.toBeInTheDocument();
  });

  it('só pede ao servidor para sortear depois da confirmação explícita', async () => {
    mockApi();
    renderAt('/events/event-1/draw', <LiveDraw />);

    const button = await screen.findByRole('button', { name: 'Iniciar sorteio' });
    await waitFor(() => expect(button).toBeEnabled());
    fireEvent.click(button);
    expect(screen.getByRole('dialog', { name: 'Confirmar sorteio' })).toBeInTheDocument();
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input, options]) => (
      String(input) === '/api/events/event-1/draw' && options?.method === 'POST'
    ))).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar e sortear' }));
    await waitFor(() => expect(vi.mocked(globalThis.fetch).mock.calls.some(([input, options]) => (
      String(input) === '/api/events/event-1/draw' && options?.method === 'POST'
    ))).toBe(true));
  });

  it('explica de forma acessível quando o pool recebido diverge do snapshot', async () => {
    mockApi();
    const currentFetch = vi.mocked(globalThis.fetch).getMockImplementation();
    vi.mocked(globalThis.fetch).mockImplementation((input, options) => {
      if (String(input).split('?')[0] === '/api/events/event-1/participants') {
        return json([
          { blazeUserId: 'blaze-1', blazeUsername: 'ana', displayName: 'Ana', enteredAt: event.openedAt },
        ]);
      }
      return currentFetch!(input, options);
    });

    renderAt('/events/event-1/draw', <LiveDraw />);

    const button = await screen.findByRole('button', { name: 'Iniciar sorteio' });
    await waitFor(() => expect(button).toBeDisabled());
    const blocker = screen.getByRole('alert');

    expect(blocker).toHaveTextContent('A lista recebida não corresponde ao snapshot final');
    expect(blocker).toHaveAttribute('id', 'draw-pool-blocker');
    expect(blocker).toHaveClass('draw-blocking-message');
    expect(button).toHaveAttribute('aria-describedby', 'draw-pool-blocker');
  });
});
