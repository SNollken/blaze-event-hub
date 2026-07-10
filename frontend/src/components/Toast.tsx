import { useState, useEffect, useCallback, useRef } from 'react';
import { X, CheckCircle, AlertTriangle, XCircle } from 'lucide-react';
import { useI18n } from '../i18n/I18nContext';

export interface ToastMessage {
  id: number;
  type: 'success' | 'error' | 'warning';
  text: string;
}

let nextId = 0;
const listeners: Array<(toasts: ToastMessage[]) => void> = [];
let toasts: ToastMessage[] = [];

function notify() {
  listeners.forEach((l) => l([...toasts]));
}

export function addToast(type: ToastMessage['type'], text: string) {
  const id = nextId++;
  toasts = [...toasts, { id, type, text }];
  notify();
  setTimeout(() => removeToast(id), 4000);
}

function removeToast(id: number) {
  toasts = toasts.filter((t) => t.id !== id);
  notify();
}

const icons = {
  success: <CheckCircle size={16} />,
  error: <XCircle size={16} />,
  warning: <AlertTriangle size={16} />,
};

export function ToastContainer() {
  const { t } = useI18n();
  const [items, setItems] = useState<ToastMessage[]>([]);
  const listenerRef = useRef(setItems);
  listenerRef.current = setItems;

  useEffect(() => {
    const listener = (t: ToastMessage[]) => listenerRef.current(t);
    listeners.push(listener);
    return () => {
      const idx = listeners.indexOf(listener);
      if (idx >= 0) listeners.splice(idx, 1);
    };
  }, []);

  return (
    <div className="toast-container">
      {items.map((toast) => (
        <div key={toast.id} className={`toast toast-${toast.type}`}>
          {icons[toast.type]}
          <span style={{ flex: 1 }}>{toast.text}</span>
          <button
            aria-label={t('closeNotification')}
            onClick={() => removeToast(toast.id)}
            style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', padding: 0, display: 'flex' }}
          >
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}

/** Hook for initial loading plus explicit refresh. */
export function usePolling<T>(fetcher: () => Promise<T>, _intervalMs = 10000) {
  const { t } = useI18n();
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const result = await fetcher();
      setData(result);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('unknownError'));
    } finally {
      setLoading(false);
    }
  }, [fetcher, t]);

	useEffect(() => {
		load();
	}, [load]);

  return { data, loading, error, reload: load };
}
