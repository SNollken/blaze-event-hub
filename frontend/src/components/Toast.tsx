import { useState, useEffect, useRef } from 'react';
import { X, CheckCircle, AlertTriangle, XCircle } from 'lucide-react';
import { t } from '../i18n';

interface ToastMessage {
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
  // Dedup: an identical (type+text) toast already on screen suppresses the
  // repeat. Happens e.g. on BlazeChannel, where the page and the Sidebar
  // AccountFooter poll getStatus independently and both fire the first-error
  // toast. Distinct types or texts still stack; after dismissal the same
  // toast may reappear.
  if (toasts.some((toast) => toast.type === type && toast.text === text)) {
    return;
  }
  const id = nextId++;
  toasts = [...toasts, { id, type, text }];
  notify();
  setTimeout(() => removeToast(id), 4000);
}

function removeToast(id: number) {
  toasts = toasts.filter((message) => message.id !== id);
  notify();
}

const icons = {
  success: <CheckCircle size={16} />,
  error: <XCircle size={16} />,
  warning: <AlertTriangle size={16} />,
};

export function ToastContainer() {
  const [items, setItems] = useState<ToastMessage[]>([]);
  const listenerRef = useRef(setItems);
  listenerRef.current = setItems;

  useEffect(() => {
    const listener = (toastList: ToastMessage[]) => listenerRef.current(toastList);
    listeners.push(listener);
    return () => {
      const idx = listeners.indexOf(listener);
      if (idx >= 0) listeners.splice(idx, 1);
    };
  }, []);

  return (
    <div className="toast-container" role="log" aria-live="polite">
      {items.map((item) => (
        <div key={item.id} className={`toast toast-${item.type}`}>
          {icons[item.type]}
          <span className="flex-1">{item.text}</span>
          <button
            className="toast-close"
            aria-label={t('toast.close')}
            onClick={() => removeToast(item.id)}
          >
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
