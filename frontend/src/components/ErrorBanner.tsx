import { AlertCircle } from 'lucide-react';
import { t } from '../i18n';

/** Minimal inline error banner with optional retry. */
export function ErrorBanner({ error, onRetry }: { error: string | null; onRetry?: () => void }) {

	if (!error) return null;
	return (
		<div className="p-3 px-4 bg-error-subtle border border-error rounded-lg text-[13px] text-error mb-4 flex items-center gap-2.5">
			<AlertCircle size={16} />
			<span className="flex-1">{error}</span>
			{onRetry && (
				<button className="btn btn-secondary btn-sm" onClick={onRetry}>
					{t('error.retry')}
				</button>
			)}
		</div>
	);
}
