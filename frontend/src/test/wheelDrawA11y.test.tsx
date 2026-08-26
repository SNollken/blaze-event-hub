import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WheelDraw } from '../components/WheelDraw';
import { axe } from './axe';

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

describe('WheelDraw a11y (axe-core)', () => {
  beforeEach(() => {
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

  it('estado pronto com participantes nao tem violacoes', async () => {
    stubApi(entries, []);
    const { container } = render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    await screen.findByText('Girar');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('estado vazio nao tem violacoes', async () => {
    stubApi([], []);
    const { container } = render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    await screen.findByText('Nenhum participante para sortear.');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('estado com participantes demais nao tem violacoes', async () => {
    const many = Array.from({ length: 33 }, (_, i) => ({ ...entries[0], id: `e${i}`, participantName: `p${i}` }));
    stubApi(many, []);
    const { container } = render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    await screen.findByText(/Participantes demais para a roleta/);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('apos sorteio (vencedor anunciado) nao tem violacoes', async () => {
    stubApi(entries, [{ entryId: 'e2', participantName: 'bob' }]);
    const { container } = render(<WheelDraw giveawayId="g1" onDrawn={() => {}} />);
    await screen.findByText('Girar');
    fireEvent.click(screen.getByText('Girar'));
    await waitFor(() => expect(screen.getByText(/Vencedor:/)).toBeInTheDocument());
    expect(await axe(container)).toHaveNoViolations();
  });
});
