import { useState, useEffect, useCallback, useRef } from 'react';
import { addToast } from '../components/Toast';

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

  // Toast on first error only — avoids spam during continued polling failures
  const hasErroredRef = useRef(false);
  useEffect(() => {
    if (error && !hasErroredRef.current) {
      addToast('error', error);
      hasErroredRef.current = true;
    } else if (!error) {
      hasErroredRef.current = false;
    }
  }, [error]);

  return { data, loading, error, reload: load };
}
