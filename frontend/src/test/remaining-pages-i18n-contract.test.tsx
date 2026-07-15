import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nContext';
import { Layout } from '../components/Layout';
import CreateEvent from '../pages/CreateEvent';
import EditEvent from '../pages/EditEvent';
import EventDetail from '../pages/EventDetail';
import EventResult from '../pages/EventResult';
import Events from '../pages/Events';
import LiveDraw from '../pages/LiveDraw';
import MyEvents from '../pages/MyEvents';
import StudioChannel from '../pages/StudioChannel';

const now = '2026-07-14T12:00:00Z';

const deterministicEvent = {
  id: 'event-1',
  creatorChannelId: 'channel-1',
  creatorChannelSlug: 'sofia',
  creatorChannelDisplayName: 'Sofia',
  creatorChannelAvatarUrl: null,
  title: 'Deterministic giveaway',
  description: 'Deterministic event description.',
  prize: 'Deterministic prize',
  entryCommand: '!join',
  status: 'COMPLETED',
  finalizedParticipantCount: 1,
  finalizedPoolHash: 'a'.repeat(64),
  startsAt: null,
  endsAt: null,
  createdAt: now,
  updatedAt: now,
  openedAt: now,
  finalizationCutoffAt: now,
  closedAt: now,
  completedAt: now,
};

const deterministicResult = {
  eventId: 'event-1',
  winnerUsername: 'viewer',
  winnerDisplayName: 'Viewer One',
  drawSeed: '123456',
  drawMethod: 'uniform_blaze_participants_v1',
  poolHash: deterministicEvent.finalizedPoolHash,
  participantCount: 1,
  selectedAt: now,
};

function json(value: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }));
}

function renderPage(path: string, route: string, element: ReactElement) {
  return render(
    <I18nProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={route} element={element} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

function installMetadataTags() {
  document.querySelectorAll(
    'meta[name="description"], meta[property="og:title"], meta[property="og:description"]',
  ).forEach((element) => element.remove());
  const description = document.createElement('meta');
  description.setAttribute('name', 'description');
  const ogTitle = document.createElement('meta');
  ogTitle.setAttribute('property', 'og:title');
  const ogDescription = document.createElement('meta');
  ogDescription.setAttribute('property', 'og:description');
  document.head.append(description, ogTitle, ogDescription);
  return { description, ogTitle, ogDescription };
}

function mockCompletedResult() {
  vi.mocked(globalThis.fetch).mockImplementation((input) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/events/event-1') return json(deterministicEvent);
    if (path === '/api/events/event-1/winner') return json(deterministicResult);
    return json({}, 404);
  });
}

function mockHistoryWithDraft() {
  vi.mocked(globalThis.fetch).mockImplementation((input) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/blaze/oauth/session') {
      return json({ connected: true, profilePresent: true });
    }
    if (path === '/api/events/my/history') {
      return json({
        drafts: [{ ...deterministicEvent, status: 'DRAFT', completedAt: null, closedAt: null }],
        active: [],
        past: [],
      });
    }
    return json({}, 404);
  });
}

function mockHistoryWithClosedLargePool() {
  vi.mocked(globalThis.fetch).mockImplementation((input) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/blaze/oauth/session') {
      return json({ connected: true, profilePresent: true });
    }
    if (path === '/api/events/my/history') {
      return json({
        drafts: [],
        active: [],
        past: [{ ...deterministicEvent, status: 'CLOSED', finalizedParticipantCount: 1_234, completedAt: null }],
      });
    }
    return json({}, 404);
  });
}

function mockOpenCreatorEvent() {
  vi.mocked(globalThis.fetch).mockImplementation((input) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/events/event-1') {
      return json({
        ...deterministicEvent,
        status: 'OPEN',
        finalizedParticipantCount: 0,
        finalizedPoolHash: null,
        closedAt: null,
        completedAt: null,
      });
    }
    if (path === '/api/events/event-1/stats') {
      return json({
        eventId: 'event-1',
        status: 'OPEN',
        participantCount: 1,
        finalizedParticipantCount: 0,
        captureActive: true,
        captureHealth: 'HEALTHY',
        lastPolledAt: now,
        lastSuccessfulPollAt: now,
        lastErrorCode: null,
        canFinalize: true,
        canDraw: false,
        openedAt: now,
        finalizationCutoffAt: null,
        closedAt: null,
        completedAt: null,
      });
    }
    if (path === '/api/events/event-1/participants') {
      return json([{ blazeUserId: 'viewer-1', blazeUsername: 'viewer', displayName: 'Viewer One', enteredAt: now }]);
    }
    return json({}, 404);
  });
}

function mockDrawWithLargePool(participantCount = 1_234) {
  vi.mocked(globalThis.fetch).mockImplementation((input) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/blaze/oauth/session') {
      return json({ connected: true, profilePresent: true, scopes: [], nextRecommendedAction: null });
    }
    if (path === '/api/members/me') {
      return json({ id: 'member-1', blazeUserId: 'creator-1', blazeUsername: 'sofia', displayName: 'Sofia', avatarUrl: null, status: 'active' });
    }
    if (path === '/api/events/event-1') {
      return json({ ...deterministicEvent, status: 'CLOSED', finalizedParticipantCount: participantCount, completedAt: null });
    }
    if (path === '/api/events/event-1/stats') {
      return json({
        eventId: 'event-1',
        status: 'CLOSED',
        participantCount,
        finalizedParticipantCount: participantCount,
        captureActive: false,
        captureHealth: 'INACTIVE',
        lastPolledAt: now,
        lastSuccessfulPollAt: now,
        lastErrorCode: null,
        canFinalize: false,
        canDraw: true,
        openedAt: now,
        finalizationCutoffAt: now,
        closedAt: now,
        completedAt: null,
      });
    }
    if (path === '/api/events/event-1/participants') {
      return json(participantCount === 1
        ? [{ blazeUserId: 'viewer-1', blazeUsername: 'viewer', displayName: 'Viewer One', enteredAt: now }]
        : []);
    }
    return json({}, 404);
  });
}

function mockStudioWithExpiry() {
  vi.mocked(globalThis.fetch).mockImplementation((input) => {
    const path = new URL(String(input), 'http://localhost').pathname;
    if (path === '/api/blaze/oauth/session') {
      return json({
        connected: true,
        profilePresent: true,
        profile: { id: 'creator-1', username: 'sofia', displayName: 'Sofia', avatarUrl: null },
        scopes: ['users.read'],
        expiresAt: now,
        nextRecommendedAction: null,
      });
    }
    if (path === '/api/members/me') {
      return json({ id: 'member-1', blazeUserId: 'creator-1', blazeUsername: 'sofia', displayName: 'Sofia', avatarUrl: null, status: 'active' });
    }
    return json({}, 404);
  });
}

describe('complete English contract for remaining pages', () => {
  it('translates the public events directory, primary CTA and filter ARIA', async () => {
    renderPage('/events', '/events', <Events />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Events' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create giveaway' })).toHaveAttribute('href', '/events/create');
    expect(screen.getByRole('group', { name: 'Filter giveaways by status' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /All/ })).toBeInTheDocument();
    expect(screen.queryByText('Exploração pública')).not.toBeInTheDocument();
    expect(screen.queryByText('Criar giveaway')).not.toBeInTheDocument();
  });

  it('formats public card timestamps in English without translating creator content', async () => {
    const creatorEvent = {
      ...deterministicEvent,
      status: 'OPEN',
      title: 'Giveaway da Sofia',
      description: 'Descrição escrita pela criadora.',
      prize: 'Prêmio surpresa',
      entryCommand: '!sorteio',
      endsAt: now,
      completedAt: null,
      closedAt: null,
    };
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      return path === '/api/events' ? json([creatorEvent]) : json({}, 404);
    });

    renderPage('/events', '/events', <Events />);

    expect(await screen.findByText('Giveaway da Sofia')).toBeInTheDocument();
    expect(screen.getByText('Descrição escrita pela criadora.')).toBeInTheDocument();
    expect(screen.getByText('Prêmio surpresa')).toBeInTheDocument();
    expect(screen.getByText('!sorteio')).toBeInTheDocument();
    expect(screen.getByText(new Intl.DateTimeFormat('en-US', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(now)))).toBeInTheDocument();
  });

  it('translates giveaway creation headings, form CTA and connected-creator ARIA', async () => {
    renderPage('/events/create', '/events/create', <CreateEvent />);

    expect(await screen.findByRole('heading', {
      level: 1,
      name: 'Prepare entries before going live',
    })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create giveaway' })).toBeInTheDocument();
    expect(screen.getByLabelText('Chat command')).toBeInTheDocument();
    expect(await screen.findByLabelText('Connected creator')).toBeInTheDocument();
    expect(screen.queryByText('Prepare a entrada antes de entrar ao vivo')).not.toBeInTheDocument();
  });

  it('returns English validation feedback from the giveaway creation form', async () => {
    renderPage('/events/create', '/events/create', <CreateEvent />);

    const submit = screen.getByRole('button', { name: /giveaway/i });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);

    expect(await screen.findByText('Give the giveaway a title.')).toBeInTheDocument();
    expect(screen.getByText('Describe the prize to be drawn.')).toBeInTheDocument();
    expect(screen.getByText('Fix the fields outlined in red before creating the giveaway.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Blaze channel')).not.toBeInTheDocument();
    expect(screen.queryByText('Dê um título ao giveaway.')).not.toBeInTheDocument();
  });

  it('does not keep validation feedback in the previous language', async () => {
    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/create']}>
          <Layout><CreateEvent /></Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    const submit = screen.getByRole('button', { name: 'Create giveaway' });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);
    expect(await screen.findByText('Give the giveaway a title.')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'pt-BR' },
    });
    await waitFor(() => {
      expect(screen.queryByText('Give the giveaway a title.')).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Criar giveaway' }));
    expect(await screen.findByText('Dê um título ao giveaway.')).toBeInTheDocument();
  });

  it('translates giveaway management navigation, lifecycle ARIA and draw CTA', async () => {
    renderPage('/events/event-1/manage', '/events/:id/manage', <EditEvent />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Giveaway de teste' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View public page' })).toHaveAttribute('href', '/events/event-1');
    expect(screen.getByRole('list', { name: 'Giveaway lifecycle' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Go to draw/ })).toHaveAttribute('href', '/events/event-1/draw');
    expect(screen.queryByText('Central de controle do giveaway')).not.toBeInTheDocument();
  });

  it('formats management timestamps with the active English locale', async () => {
    mockOpenCreatorEvent();
    renderPage('/events/event-1/manage', '/events/:id/manage', <EditEvent />);

    const expected = new Intl.DateTimeFormat('en', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(now));
    expect((await screen.findAllByText(expected)).length).toBeGreaterThanOrEqual(2);
  });

  it('translates public giveaway instructions, stream CTA and summary ARIA', async () => {
    renderPage('/events/event-1', '/events/:id', <EventDetail />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Giveaway de teste' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open stream/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Send this command in chat' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Giveaway summary' })).toBeInTheDocument();
    expect(screen.queryByText('Envie este comando no chat')).not.toBeInTheDocument();
  });

  it('formats public giveaway timestamps in English without changing the entry command', async () => {
    renderPage('/events/event-1', '/events/:id', <EventDetail />);

    const expected = new Intl.DateTimeFormat('en', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(now));
    expect((await screen.findAllByText(expected)).length).toBeGreaterThanOrEqual(3);
    expect(screen.getByText('!participar')).toBeInTheDocument();
  });

  it('keeps event-detail metadata specific and localized after changing language', async () => {
    mockCompletedResult();
    const { description, ogTitle, ogDescription } = installMetadataTags();

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/event-1']}>
          <Layout>
            <Routes><Route path="/events/:id" element={<EventDetail />} /></Routes>
          </Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Deterministic giveaway' })).toBeInTheDocument();
    await waitFor(() => {
      expect(document.title).toBe('Deterministic giveaway | Blaze Event Hub');
      expect(description).toHaveAttribute(
        'content',
        'Deterministic giveaway: follow the command, status, and result of this giveaway.',
      );
      expect(ogTitle).toHaveAttribute('content', 'Deterministic giveaway | Blaze Event Hub');
      expect(ogDescription).toHaveAttribute(
        'content',
        'Deterministic giveaway: follow the command, status, and result of this giveaway.',
      );
    });

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'pt-BR' },
    });

    await waitFor(() => {
      expect(document.title).toBe('Deterministic giveaway | Blaze Event Hub');
      expect(description).toHaveAttribute(
        'content',
        'Deterministic giveaway: acompanhe o comando, o estado e o resultado deste giveaway.',
      );
      expect(ogTitle).toHaveAttribute('content', 'Deterministic giveaway | Blaze Event Hub');
    });

    description.remove();
    ogTitle.remove();
    ogDescription.remove();
  });

  it('replaces stale metadata with event-detail defaults when loading fails', async () => {
    vi.mocked(globalThis.fetch).mockImplementation(() => json({}, 404));
    const { description, ogTitle, ogDescription } = installMetadataTags();
    document.title = 'Previous route';

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/missing']}>
          <Layout>
            <Routes><Route path="/events/:id" element={<EventDetail />} /></Routes>
          </Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Giveaway unavailable' })).toBeInTheDocument();
    await waitFor(() => {
      expect(document.title).toBe('Giveaway details | Blaze Event Hub');
      expect(description).toHaveAttribute('content', 'Follow the giveaway status, entry command, and result.');
      expect(ogTitle).toHaveAttribute('content', 'Giveaway details | Blaze Event Hub');
      expect(ogDescription).toHaveAttribute('content', 'Follow the giveaway status, entry command, and result.');
    });
  });

  it('translates the live draw heading, action, stage and chance ARIA', async () => {
    renderPage('/events/event-1/draw', '/events/:id/draw', <LiveDraw />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Live draw' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Start draw' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Draw stage' })).toBeInTheDocument();
    expect(screen.getByLabelText('One chance')).toBeInTheDocument();
    expect(screen.queryByText('Sorteio ao vivo')).not.toBeInTheDocument();
  });

  it('formats the finalized pool count with the active English locale', async () => {
    mockDrawWithLargePool();
    renderPage('/events/event-1/draw', '/events/:id/draw', <LiveDraw />);

    expect(await screen.findByText(new Intl.NumberFormat('en').format(1_234))).toBeInTheDocument();
  });

  it('uses singular copy when confirming a draw with one participant', async () => {
    mockDrawWithLargePool(1);
    renderPage('/events/event-1/draw', '/events/:id/draw', <LiveDraw />);

    fireEvent.click(await screen.findByRole('button', { name: 'Start draw' }));

    expect(screen.getByText(
      'The server will choose the winner from 1 participant. The winner will be persisted only once, and the result will become public.',
    )).toBeInTheDocument();
    expect(screen.queryByText(/1 participants/)).not.toBeInTheDocument();
  });

  it('translates the public result proof, CTAs and draw timestamp copy', async () => {
    mockCompletedResult();
    renderPage('/events/event-1/result', '/events/:id/result', <EventResult />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Viewer One' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Confirmed winner' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Draw technical record' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View giveaway details' })).toHaveAttribute('href', '/events/event-1');
    expect(screen.getByRole('link', { name: 'Explore other giveaways' })).toHaveAttribute('href', '/events');
    expect(screen.getByText(/^Drawn on /)).toBeInTheDocument();
    expect(screen.queryByText('Vencedor confirmado')).not.toBeInTheDocument();
  });

  it('formats result timestamps and participant totals with the active English locale', async () => {
    vi.mocked(globalThis.fetch).mockImplementation((input) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/events/event-1') return json(deterministicEvent);
      if (path === '/api/events/event-1/winner') {
        return json({ ...deterministicResult, participantCount: 1_234 });
      }
      return json({}, 404);
    });
    renderPage('/events/event-1/result', '/events/:id/result', <EventResult />);

    const expectedDate = new Intl.DateTimeFormat('en', {
      dateStyle: 'long',
      timeStyle: 'medium',
    }).format(new Date(now));
    expect(await screen.findByText(new Intl.NumberFormat('en').format(1_234))).toBeInTheDocument();
    expect(screen.getAllByText(expectedDate).length).toBeGreaterThanOrEqual(2);
  });

  it('keeps result metadata specific and localized after changing language', async () => {
    mockCompletedResult();
    const { description, ogTitle, ogDescription } = installMetadataTags();

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/event-1/result']}>
          <Layout>
            <Routes><Route path="/events/:id/result" element={<EventResult />} /></Routes>
          </Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Viewer One' })).toBeInTheDocument();
    await waitFor(() => {
      expect(document.title).toBe('Deterministic giveaway: result | Blaze Event Hub');
      expect(description).toHaveAttribute(
        'content',
        'Deterministic giveaway: review the confirmed winner and technical draw record.',
      );
      expect(ogTitle).toHaveAttribute('content', 'Deterministic giveaway: result | Blaze Event Hub');
      expect(ogDescription).toHaveAttribute(
        'content',
        'Deterministic giveaway: review the confirmed winner and technical draw record.',
      );
    });

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'pt-BR' },
    });

    await waitFor(() => {
      expect(document.title).toBe('Deterministic giveaway: resultado | Blaze Event Hub');
      expect(description).toHaveAttribute(
        'content',
        'Deterministic giveaway: consulte o vencedor confirmado e o registro técnico do sorteio.',
      );
      expect(ogTitle).toHaveAttribute('content', 'Deterministic giveaway: resultado | Blaze Event Hub');
    });

    description.remove();
    ogTitle.remove();
    ogDescription.remove();
  });

  it('replaces stale metadata with result defaults when loading fails', async () => {
    vi.mocked(globalThis.fetch).mockImplementation(() => json({}, 404));
    const { description, ogTitle, ogDescription } = installMetadataTags();
    document.title = 'Previous route';

    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/events/missing/result']}>
          <Layout>
            <Routes><Route path="/events/:id/result" element={<EventResult />} /></Routes>
          </Layout>
        </MemoryRouter>
      </I18nProvider>,
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Result unavailable' })).toBeInTheDocument();
    await waitFor(() => {
      expect(document.title).toBe('Giveaway result | Blaze Event Hub');
      expect(description).toHaveAttribute('content', 'Review the winner and the technical drawing record.');
      expect(ogTitle).toHaveAttribute('content', 'Giveaway result | Blaze Event Hub');
      expect(ogDescription).toHaveAttribute('content', 'Review the winner and the technical drawing record.');
    });
  });

  it('translates creator history headings, CTA and summary ARIA', async () => {
    mockHistoryWithDraft();
    renderPage('/my-events', '/my-events', <MyEvents />);

    expect(await screen.findByRole('heading', { level: 1, name: 'My events' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create giveaway' })).toHaveAttribute('href', '/events/create');
    expect(await screen.findByLabelText('Your events summary')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Drafts' })).toBeInTheDocument();
    expect(screen.queryByText('Painel do criador')).not.toBeInTheDocument();
  });

  it('formats creator history pool totals with the active English locale', async () => {
    mockHistoryWithClosedLargePool();
    renderPage('/my-events', '/my-events', <MyEvents />);

    expect(await screen.findByText('1,234 in the pool')).toBeInTheDocument();
  });

  it('translates Blaze connection heading, actions and connected-account ARIA', async () => {
    renderPage('/settings/blaze', '/settings/blaze', <StudioChannel />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Your stream connection' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Refresh status' })).toBeInTheDocument();
    expect(await screen.findByLabelText('Connected Blaze account')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'The secret never reaches the browser' })).toBeInTheDocument();
    expect(screen.queryByText('Sua conexão com a transmissão')).not.toBeInTheDocument();
  });

  it('formats the OAuth expiry with the active English locale', async () => {
    mockStudioWithExpiry();
    renderPage('/settings/blaze', '/settings/blaze', <StudioChannel />);

    const expected = new Intl.DateTimeFormat('en', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(now));
    expect(await screen.findByText(expected)).toBeInTheDocument();
  });
});

describe('saved Brazilian Portuguese locale', () => {
  it('keeps a representative Events smoke in pt-BR', async () => {
    localStorage.setItem('blaze-event-hub:language', 'pt-BR');
    renderPage('/events', '/events', <Events />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Eventos' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Criar giveaway' })).toBeInTheDocument();
    await waitFor(() => expect(document.documentElement.lang).toBe('pt-BR'));
  });
});
