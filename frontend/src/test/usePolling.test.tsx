import { renderHook, waitFor } from '@testing-library/react';
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

  afterEach(() => {
    // cleanup from setup.ts runs automatically; nothing extra needed
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
