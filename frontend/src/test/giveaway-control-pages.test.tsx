import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CreateEvent from '../pages/CreateEvent';
import EditEvent from '../pages/EditEvent';
import StudioChannel from '../pages/StudioChannel';
import { I18nProvider } from '../i18n/I18nContext';

function json(value: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }));
}

const openEvent = {
  id: 'event-1',
  creatorChannelId: 'channel-real-42',
  creatorChannelSlug: 'canal-sofia',
  creatorChannelDisplayName: 'Canal da Sofia',
  creatorChannelAvatarUrl: null,
  title: 'Giveaway da comunidade',
  description: 'Teste do fluxo ao vivo.',
  prize: 'Gift card de R$ 200',
  entryCommand: '!participar',
  status: 'OPEN',
  finalizedParticipantCount: 0,
  finalizedPoolHash: null,
  startsAt: null,
  endsAt: null,
  createdAt: '2026-07-14T10:00:00Z',
  updatedAt: '2026-07-14T10:00:00Z',
  openedAt: '2026-07-14T10:05:00Z',
  finalizationCutoffAt: null,
  closedAt: null,
  completedAt: null,
};

describe('páginas de controle do giveaway', () => {
  beforeEach(() => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
  });

  it('permite enviar o formulário vazio e associa os erros aos campos', () => {
    render(<I18nProvider><MemoryRouter><CreateEvent /></MemoryRouter></I18nProvider>);

    const submit = screen.getByRole('button', { name: 'Criar giveaway' });
    expect(submit).toBeEnabled();
    fireEvent.click(submit);

    const title = screen.getByLabelText('Título');
    const prize = screen.getByLabelText('Prêmio');
    const command = screen.getByLabelText('Comando do chat');
    const channel = screen.getByLabelText('Canal na Blaze');
    expect(title).toHaveAttribute('aria-invalid', 'true');
    expect(title).toHaveAttribute('aria-describedby', 'event-title-error');
    expect(prize).toHaveAttribute('aria-invalid', 'true');
    expect(command).toHaveAttribute('aria-invalid', 'false');
    expect(channel).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Sorteie uma pessoa')).toBeInTheDocument();
  });

  it('envia o slug para o backend validar e resolver novamente', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url === '/api/members/me') {
        return json({
          id: 'member-1',
          blazeUserId: 'user-id-que-nao-e-canal',
          blazeUsername: 'sofia',
          displayName: 'Sofia',
          avatarUrl: null,
          status: 'ACTIVE',
        });
      }
      if (url.startsWith('/api/blaze/channels/resolve')) {
        return json({ id: 'channel-real-42', slug: 'canal-sofia', displayName: 'Canal da Sofia', avatarUrl: null });
      }
      if (url === '/api/events' && options?.method === 'POST') {
        return json({ ...openEvent, status: 'DRAFT' }, 201);
      }
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<I18nProvider><MemoryRouter><CreateEvent /></MemoryRouter></I18nProvider>);

    const creatorCard = await screen.findByLabelText('Criador conectado');
    const creatorIdentity = creatorCard.querySelector<HTMLElement>('.creator-identity');
    expect(creatorIdentity).not.toBeNull();
    expect(within(creatorIdentity!).getByText('Sofia')).toBeInTheDocument();
    expect(within(creatorIdentity!).getByText('@sofia')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Giveaway da comunidade' } });
    fireEvent.change(screen.getByLabelText('Prêmio'), { target: { value: 'Gift card de R$ 200' } });
    fireEvent.change(screen.getByLabelText('Canal na Blaze'), { target: { value: 'https://blaze.stream/canal-sofia' } });
    fireEvent.click(screen.getByRole('button', { name: 'Localizar canal' }));

    expect(await screen.findByText('@canal-sofia')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Criar giveaway' }));

    await waitFor(() => {
      const createCall = fetchMock.mock.calls.find(([input, options]) => input === '/api/events' && options?.method === 'POST');
      expect(createCall).toBeDefined();
      const payload = JSON.parse(String(createCall?.[1]?.body));
      expect(payload.creatorChannelSlug).toBe('canal-sofia');
      expect(payload.creatorChannelId).toBeUndefined();
      expect(payload.entryCommand).toBe('!participar');
    });
  });

  it('exige confirmação explícita antes de congelar o pool', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url === '/api/events/event-1') return json(openEvent);
      if (url === '/api/events/event-1/stats') {
        return json({
          eventId: 'event-1',
          status: 'OPEN',
          participantCount: 1,
          finalizedParticipantCount: 0,
          captureActive: true,
          captureHealth: 'HEALTHY',
          lastPolledAt: '2026-07-14T10:06:00Z',
          lastSuccessfulPollAt: '2026-07-14T10:06:00Z',
          lastErrorCode: null,
          canFinalize: true,
          canDraw: false,
          openedAt: openEvent.openedAt,
          closedAt: null,
          completedAt: null,
        });
      }
      if (url === '/api/events/event-1/participants') {
        return json([{
          blazeUserId: 'participant-1',
          blazeUsername: 'participante',
          displayName: 'Participante',
          enteredAt: '2026-07-14T10:06:00Z',
        }]);
      }
      if (url === '/api/events/event-1/finalize' && options?.method === 'POST') {
        return json({
          ...openEvent,
          status: 'CLOSED',
          finalizedParticipantCount: 1,
          finalizedPoolHash: 'hash-verificavel',
          finalizationCutoffAt: '2026-07-14T10:10:00Z',
          closedAt: '2026-07-14T10:10:00Z',
        });
      }
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/event-1/manage']}>
          <Routes>
            <Route path="/events/:id/manage" element={<EditEvent />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Finalizar evento' }));
    expect(screen.getByText('Participante').closest('li')).toHaveClass('participant-item');
    expect(screen.getByText(/Esta ação é irreversível/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/events/event-1/finalize',
      expect.objectContaining({ method: 'POST' }),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Finalizar com 1 participante' }));
    expect(await screen.findByText('Pool final registrado')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/events/event-1/finalize',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('exige confirmação antes de cancelar um evento com captura aberta', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url === '/api/events/event-1') return json(openEvent);
      if (url === '/api/events/event-1/stats') {
        return json({
          eventId: 'event-1',
          status: 'OPEN',
          participantCount: 1,
          finalizedParticipantCount: 0,
          captureActive: true,
          captureHealth: 'HEALTHY',
          lastPolledAt: '2026-07-14T10:06:00Z',
          lastSuccessfulPollAt: '2026-07-14T10:06:00Z',
          lastErrorCode: null,
          canFinalize: true,
          canDraw: false,
          openedAt: openEvent.openedAt,
          closedAt: null,
          completedAt: null,
        });
      }
      if (url === '/api/events/event-1/participants') return json([]);
      if (url === '/api/events/event-1/cancel' && options?.method === 'POST') {
        return json({ ...openEvent, status: 'CANCELLED' });
      }
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/event-1/manage']}>
          <Routes>
            <Route path="/events/:id/manage" element={<EditEvent />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar evento' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancelar este evento aberto?' });
    expect(dialog).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, options]) => (
      input === '/api/events/event-1/cancel' && options?.method === 'POST'
    ))).toBe(false);

    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar evento' }));
    expect(await screen.findByRole('heading', { name: 'Este evento foi encerrado' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, options]) => (
      input === '/api/events/event-1/cancel' && options?.method === 'POST'
    ))).toBe(true);
  });

  it('trata FINALIZING como estado transitório bloqueado para novas ações', async () => {
    const cutoff = '2026-07-14T10:10:00Z';
    const finalizingEvent = { ...openEvent, status: 'FINALIZING', finalizationCutoffAt: cutoff };
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const url = String(input);
      if (url === '/api/events/event-1') return json(finalizingEvent);
      if (url === '/api/events/event-1/stats') {
        return json({
          eventId: 'event-1',
          status: 'FINALIZING',
          participantCount: 3,
          finalizedParticipantCount: 0,
          captureActive: false,
          captureHealth: 'FINALIZING',
          lastPolledAt: cutoff,
          lastSuccessfulPollAt: cutoff,
          lastErrorCode: null,
          canFinalize: false,
          canDraw: false,
          openedAt: openEvent.openedAt,
          closedAt: null,
          completedAt: null,
        });
      }
      if (url === '/api/events/event-1/participants') return json([]);
      return json({});
    });

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/event-1/manage']}>
          <Routes>
            <Route path="/events/:id/manage" element={<EditEvent />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByText('Fechando entradas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finalizando...' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Cancelar evento' })).not.toBeInTheDocument();
    expect(screen.getByText('Entradas aceitas até')).toBeInTheDocument();
  });

  it('não apresenta estado desconectado quando a consulta da conexão falha', async () => {
    vi.mocked(globalThis.fetch).mockRejectedValue(new Error('Falha ao consultar OAuth'));

    render(
      <I18nProvider>
        <MemoryRouter><StudioChannel /></MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('O estado da conta não está disponível no momento.');
    expect(screen.queryByText('Falha ao consultar OAuth')).not.toBeInTheDocument();
    expect(screen.queryByText('Nenhuma conta Blaze conectada')).not.toBeInTheDocument();
  });

  it('separa nome, handle e estado na identidade da conexão Blaze', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const url = String(input);
      if (url === '/api/blaze/oauth/session') {
        return json({
          connected: true,
          profilePresent: true,
          profile: { id: 'blaze-1', username: 'sofia', displayName: 'Sofia', avatarUrl: null },
        });
      }
      if (url === '/api/members/me') {
        return json({
          id: 'member-1',
          blazeUserId: 'blaze-1',
          blazeUsername: 'sofia',
          displayName: 'Sofia',
          avatarUrl: null,
          status: 'ACTIVE',
        });
      }
      return json({});
    });

    render(
      <I18nProvider>
        <MemoryRouter><StudioChannel /></MemoryRouter>
      </I18nProvider>,
    );

    const profile = await screen.findByLabelText('Conta Blaze conectada');
    const identity = profile.querySelector<HTMLElement>('.creator-identity');

    expect(identity).not.toBeNull();
    expect(within(identity!).getByText('Sofia')).toBeInTheDocument();
    expect(within(identity!).getByText('@sofia')).toBeInTheDocument();
    expect(within(identity!).getByText('Conectado')).toBeInTheDocument();
  });
});
