import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePolling } from '../components/Toast';

describe('usePolling', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('agenda uma nova execução após o intervalo configurado', async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn().mockResolvedValue('ok');

    renderHook(() => usePolling(fetcher, 1_000));

    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => {
      await Promise.resolve();
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });

    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('cancela o timer ao desmontar', async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn().mockResolvedValue('ok');
    const { unmount } = renderHook(() => usePolling(fetcher, 1_000));

    await act(async () => {
      await Promise.resolve();
    });
    expect(fetcher).toHaveBeenCalledTimes(1);

    unmount();

    await act(async () => {
      vi.advanceTimersByTime(3_000);
      await Promise.resolve();
    });

    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('não sobrepõe chamadas assíncronas', async () => {
    vi.useFakeTimers();
    let resolveFirst!: (value: string) => void;
    const firstRequest = new Promise<string>((resolve) => {
      resolveFirst = resolve;
    });
    const fetcher = vi.fn()
      .mockImplementationOnce(() => firstRequest)
      .mockResolvedValue('seguinte');

    renderHook(() => usePolling(fetcher, 1_000));
    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(3_000);
      await Promise.resolve();
    });

    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveFirst('primeiro');
      await firstRequest;
    });
    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });

    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('pausa atualizações em segundo plano e recarrega ao voltar para a aba', async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn().mockResolvedValue('ok');
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });

    renderHook(() => usePolling(fetcher, 1_000));
    await act(async () => { await Promise.resolve(); });
    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(3_000);
      await Promise.resolve();
    });
    expect(fetcher).toHaveBeenCalledTimes(1);

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('ignora resposta antiga quando o fetcher muda durante uma requisição', async () => {
    let resolveFirst!: (value: string) => void;
    let resolveSecond!: (value: string) => void;
    const first = vi.fn(() => new Promise<string>((resolve) => { resolveFirst = resolve; }));
    const second = vi.fn(() => new Promise<string>((resolve) => { resolveSecond = resolve; }));
    const { result, rerender } = renderHook(
      ({ fetcher }) => usePolling(fetcher, 60_000),
      { initialProps: { fetcher: first } },
    );

    rerender({ fetcher: second });
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveFirst('antigo');
      await Promise.resolve();
    });
    expect(result.current.data).not.toBe('antigo');

    await act(async () => {
      resolveSecond('atual');
      await Promise.resolve();
    });
    expect(result.current.data).toBe('atual');
  });
});
