import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WheelDraw } from '../components/WheelDraw';

const entries = [
  { id: 'e1', giveawayId: 'g1', participantName: 'alice', enteredAt: '2026-08-13T00:00:00Z', selected: false, eligible: true },
  { id: 'e2', giveawayId: 'g1', participantName: 'bob', enteredAt: '2026-08-13T00:00:01Z', selected: false, eligible: true },
  { id: 'e3', giveawayId: 'g1', participantName: 'carol', enteredAt: '2026-08-13T00:00:02Z', selected: false, eligible: true },
];

function stubApi(entriesResponse: unknown, winners: Array<{ entryId: string; participantName: string }>) {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    const body =
      url.includes('/entries') ? entriesResponse
      : url.includes('/results') ? {
          giveawayId: 'g1', title: 'Roleta', status: 'COMPLETED', totalEntries: 3,
          winnerCount: 1, winners: winners.map((w) => ({ ...w, enteredAt: '2026-08-13T00:00:00Z' })),
          drawnAt: '2026-08-13T00:05:00Z',
        }
      : { id: 'g1', status: 'COMPLETED' };
    return Promise.resolve(new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('WheelDraw', () => {
  beforeEach(() => {
    // reduced motion ON: deterministic path (no CSS transition to wait for)
    vi.stubGlobal('matchMedia', vi.fn().mockImplementation((query: string) => ({
      matches: query === '(prefers-reduced-motion: reduce)',
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })));
  });

  it('shows empty state when the giveaway has no participants', async () => {
    stubApi([], []);
    render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    expect(await screen.findByText('Nenhum participante para sortear.')).toBeInTheDocument();
  });

  it('offers quick draw when there are more than 32 participants', async () => {
    const many = Array.from({ length: 33 }, (_, i) => ({ ...entries[0], id: `e${i}`, participantName: `p${i}` }));
    stubApi(many, []);
    render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    expect(await screen.findByText(/Participantes demais para a roleta/)).toBeInTheDocument();
    expect(screen.getByText('Sorteio rápido')).toBeInTheDocument();
  });

  it('spins, draws and announces the winner (reduced motion path)', async () => {
    const fetchMock = stubApi(entries, [{ entryId: 'e2', participantName: 'bob' }]);
    const onDrawn = vi.fn();

    render(<WheelDraw giveawayId="g1" onDrawn={onDrawn} />);

    // names rendered as segments
    expect(await screen.findByText('alice')).toBeInTheDocument();
    expect(screen.getByText('bob')).toBeInTheDocument();
    expect(screen.getByText('carol')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Girar'));

    await waitFor(() => expect(screen.getByText(/Vencedor:/)).toBeInTheDocument());
    expect(screen.getByText('bob', { selector: '.text-primary' })).toBeInTheDocument();
    expect(onDrawn).toHaveBeenCalledTimes(1);

    const calledUrls = fetchMock.mock.calls.map((call) => String(call[0]));
    expect(calledUrls.some((url) => url.includes('/api/giveaways/g1/draw'))).toBe(true);
    expect(calledUrls.some((url) => url.includes('/api/giveaways/g1/results'))).toBe(true);
  });

  it('does not announce a winner before spinning', async () => {
    stubApi(entries, []);
    render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    await screen.findByText('Girar');
    expect(screen.queryByText(/Vencedor:/)).not.toBeInTheDocument();
  });
});
