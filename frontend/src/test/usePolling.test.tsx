import { render, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Mock } from 'vitest';
import { usePolling } from '../hooks/usePolling';

const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('../components/Toast', () => ({
  addToast: mockAddToast,
}));

describe('usePolling error handling', () => {
  beforeEach(() => {
    mockAddToast.mockClear();
  });

  it('toasts once on first fetch error, not on subsequent polling failures', async () => {
    const failingFetcher: Mock<() => Promise<never>> = vi.fn().mockRejectedValue(new Error('API down'));

    const { unmount } = renderHook(() => usePolling(failingFetcher, 50));

    // Initial mount → first fetch fails → toast
    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('error', 'API down');
    });
    expect(mockAddToast).toHaveBeenCalledTimes(1);

    // Let several polling cycles run — same error, no additional toasts
    await new Promise(resolve => setTimeout(resolve, 300));

    // At least a few polls happened, but only ONE toast
    expect(failingFetcher).toHaveBeenCalled();
    expect(failingFetcher.mock.calls.length).toBeGreaterThanOrEqual(3);
    expect(mockAddToast).toHaveBeenCalledTimes(1);

    unmount();
  });

  it('clears error state when fetch succeeds after previous failure', async () => {
    const fetcher: Mock<() => Promise<string>> = vi.fn()
      .mockRejectedValueOnce(new Error('API down'))
      .mockResolvedValue('ok');

    const { result, unmount } = renderHook(() => usePolling(fetcher, 50));

    // First attempt fails → toast
    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('error', 'API down');
    });
    expect(mockAddToast).toHaveBeenCalledTimes(1);

    // Second poll succeeds → data set, error cleared, no new toast
    await waitFor(() => {
      expect(result.current.data).toBe('ok');
    });
    expect(result.current.error).toBe(null);
    expect(mockAddToast).toHaveBeenCalledTimes(1);

    unmount();
  });
});

/**
 * Regression guard: an inline arrow fetcher (new identity on every render)
 * used to retrigger the initial-load effect after each resolved fetch, which
 * re-rendered the component and recreated the fetcher -> infinite refetch
 * loop (observed as /api/status spam from Sidebar/AccountFooter, and as a
 * hung jsdom test where timers were starved).
 */
describe('usePolling refetch stability', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('fetcher inline instavel nao dispara refetch em loop', async () => {
    const fetcher = vi.fn(async () => ({ n: 1 }));
    function Probe() {
      // Inline arrow: new function identity on every render (worst case).
      usePolling(() => fetcher(), 60000);
      return null;
    }
    render(<Probe />);
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(fetcher).toHaveBeenCalledTimes(1);
  }, 3000);

  it('continua pollando no intervalo definido', async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn(async () => ({ n: 1 }));
    function Probe() {
      usePolling(fetcher, 5000);
      return null;
    }
    render(<Probe />);
    await vi.advanceTimersByTimeAsync(0);
    expect(fetcher).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(5000);
    expect(fetcher).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(5000);
    expect(fetcher).toHaveBeenCalledTimes(3);
  });
});
