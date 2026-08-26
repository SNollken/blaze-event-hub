import { useCallback, useEffect, useRef, useState } from 'react';
import { addToast } from '../components/Toast';
import { t } from '../i18n';

/** Hook for initial loading plus automatic polling at the given interval. */
export function usePolling<T>(fetcher: () => Promise<T>, intervalMs = 10000) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // The fetcher is read through a ref so its IDENTITY never drives refetches.
  // Callers may pass inline arrows (new identity every render); keying the
  // load effect on such a fetcher retriggered it after every resolved fetch,
  // and each setData re-rendered the caller -> infinite refetch loop.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const load = useCallback(async () => {
    try {
      const result = await fetcherRef.current();
      setData(result);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('common.unknownError'));
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load on mount
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