import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ToastContainer } from '../components/Toast';
import { I18nProvider } from '../i18n/I18nContext';
import CreateEvent from '../pages/CreateEvent';
import EditEvent from '../pages/EditEvent';
import EventDetail from '../pages/EventDetail';
import LiveDraw from '../pages/LiveDraw';
import StudioChannel from '../pages/StudioChannel';

function json(value: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }));
}

const event = {
  id: 'event-1',
  creatorChannelId: 'channel-1',
  creatorChannelSlug: 'sofia',
  creatorChannelDisplayName: 'Sofia',
  creatorChannelAvatarUrl: null,
  title: 'Community giveaway',
  description: 'A safe public description.',
  prize: 'Gift card',
  entryCommand: '!join',
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

function renderRoute(path: string, route: string, page: React.ReactNode) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path={route} element={page} /></Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

describe('erros seguros nas páginas', () => {
  it('não exibe a mensagem técnica ao falhar a resolução do canal em inglês', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/members/me') {
        return json({
          id: 'member-1',
          blazeUserId: 'creator-1',
          blazeUsername: 'sofia',
          displayName: 'Sofia',
          avatarUrl: null,
          status: 'active',
        });
      }
      if (path === '/api/blaze/channels/resolve') {
        return json({ message: 'database.internal:5432 leaked by upstream' }, 502);
      }
      return json({}, 404);
    });

    render(
      <I18nProvider>
        <MemoryRouter><CreateEvent /></MemoryRouter>
      </I18nProvider>,
    );

    fireEvent.change(screen.getByLabelText('Blaze channel'), { target: { value: 'sofia' } });
    fireEvent.click(screen.getByRole('button', { name: 'Find channel' }));

    expect(await screen.findByText('We could not find this channel on Blaze.')).toBeInTheDocument();
    expect(screen.queryByText(/database\.internal/i)).not.toBeInTheDocument();
  });

  it('does not expose an API message when giveaway creation fails', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input, options) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/members/me') {
        return json({
          id: 'member-1',
          blazeUserId: 'creator-1',
          blazeUsername: 'sofia',
          displayName: 'Sofia',
          avatarUrl: null,
          status: 'active',
        });
      }
      if (path === '/api/blaze/channels/resolve') {
        return json({ id: 'channel-1', slug: 'sofia', displayName: 'Sofia', avatarUrl: null });
      }
      if (path === '/api/events' && options?.method === 'POST') {
        return json({ message: 'database.internal:5432 leaked by create' }, 502);
      }
      return json({}, 404);
    });

    render(
      <I18nProvider>
        <MemoryRouter><CreateEvent /></MemoryRouter>
      </I18nProvider>,
    );

    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Community giveaway' } });
    fireEvent.change(screen.getByLabelText('Prize'), { target: { value: 'Gift card' } });
    fireEvent.change(screen.getByLabelText('Blaze channel'), { target: { value: 'sofia' } });
    fireEvent.click(screen.getByRole('button', { name: 'Find channel' }));
    expect((await screen.findAllByText('@sofia')).length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'Create giveaway' }));

    expect(await screen.findByText('We could not create the giveaway.')).toBeInTheDocument();
    expect(screen.queryByText(/database\.internal/i)).not.toBeInTheDocument();
  });

  it('does not expose an API message when the management page fails to load', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/events/event-1') {
        return json({ message: 'proxy HTML from private-upstream.local' }, 502);
      }
      return json({}, 404);
    });

    renderRoute('/events/event-1/manage', '/events/:id/manage', <EditEvent />);

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load the giveaway.');
    expect(screen.queryByText(/private-upstream/i)).not.toBeInTheDocument();
  });

  it('replaces synchronization codes and polling errors with translated copy', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/events/event-1') return json(event);
      if (path === '/api/events/event-1/stats') {
        return json({
          eventId: 'event-1',
          status: 'OPEN',
          participantCount: 0,
          finalizedParticipantCount: 0,
          captureActive: true,
          captureHealth: 'DEGRADED',
          lastPolledAt: event.openedAt,
          lastSuccessfulPollAt: event.openedAt,
          lastErrorCode: 'DATABASE_DOWNSTREAM_42',
          canFinalize: false,
          canDraw: false,
          openedAt: event.openedAt,
          closedAt: null,
          completedAt: null,
        });
      }
      if (path === '/api/events/event-1/participants') {
        return json({ message: 'participant service at 10.0.0.8 failed' }, 502);
      }
      return json({}, 404);
    });

    renderRoute('/events/event-1/manage', '/events/:id/manage', <EditEvent />);

    expect(await screen.findByText('Synchronization needs attention. Try refreshing in a moment.')).toBeInTheDocument();
    expect(screen.queryByText(/DATABASE_DOWNSTREAM_42|10\.0\.0\.8/i)).not.toBeInTheDocument();
  });

  it('does not expose an API message on the public event detail', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/events/event-1') {
        return json({ message: '<html>edge proxy private detail</html>' }, 502);
      }
      return json({}, 404);
    });

    renderRoute('/events/event-1', '/events/:id', <EventDetail />);

    expect(await screen.findByText('This giveaway could not be found.')).toBeInTheDocument();
    expect(screen.queryByText(/private detail/i)).not.toBeInTheDocument();
  });

  it('does not expose an API message while preparing the draw', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/blaze/oauth/session') {
        return json({ message: 'oauth proxy exposed an internal hostname' }, 502);
      }
      return json({}, 404);
    });

    renderRoute('/events/event-1/draw', '/events/:id/draw', <LiveDraw />);

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not prepare this draw.');
    expect(screen.queryByText(/internal hostname/i)).not.toBeInTheDocument();
  });

  it('uses translated copy for OAuth connection failures', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/blaze/oauth/session') {
        return json({ connected: false, scopes: [] });
      }
      if (path === '/api/blaze/oauth/start') {
        return json({ message: 'render.internal OAuth configuration leaked' }, 503);
      }
      return json({}, 404);
    });

    render(
      <I18nProvider>
        <MemoryRouter>
          <StudioChannel />
          <ToastContainer />
        </MemoryRouter>
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Connect with Blaze' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not start the Blaze connection.');
    expect(screen.queryByText(/render\.internal/i)).not.toBeInTheDocument();
  });

  it('uses translated copy when loading the OAuth status itself fails', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/blaze/oauth/session') {
        return json({ message: 'oauth-status.service.consul unavailable' }, 502);
      }
      return json({}, 404);
    });

    render(
      <I18nProvider>
        <MemoryRouter><StudioChannel /></MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Account status is currently unavailable.');
    expect(screen.queryByText(/service\.consul/i)).not.toBeInTheDocument();
  });
});
