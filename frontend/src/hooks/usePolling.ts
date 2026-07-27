import { useState, useEffect, useCallback } from 'react';

/** Hook for initial loading plus automatic polling at the given interval. */
export function usePolling<T>(fetcher: () => Promise<T>, intervalMs = 10000) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const result = await fetcher();
      setData(result);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Erro desconhecido');
    } finally {
      setLoading(false);
    }
  }, [fetcher]);

  // Initial load on mount (and whenever fetcher changes)
  useEffect(() => {
    load();
  }, [load]);

  // Set up polling interval with proper cleanup
  useEffect(() => {
    if (intervalMs <= 0) return;
    const id = setInterval(() => {
      load();
    }, intervalMs);
    return () => clearInterval(id);
  }, [load, intervalMs]);

  return { data, loading, error, reload: load };
}
