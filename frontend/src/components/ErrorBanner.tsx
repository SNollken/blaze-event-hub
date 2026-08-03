import { AlertCircle } from 'lucide-react';
import { t } from '../i18n';

/** Minimal inline error banner with optional retry. */
export function ErrorBanner({ error, onRetry }: { error: string | null; onRetry?: () => void }) {
	if (!error) return null;
	return (
		<div
			style={{
				padding: '12px 16px',
				background: 'var(--error-subtle)',
				border: '1px solid var(--error)',
				borderRadius: 'var(--radius)',
				color: 'var(--error)',
				fontSize: 13,
				marginBottom: 16,
				display: 'flex',
				alignItems: 'center',
				gap: 10,
			}}
		>
			<AlertCircle size={16} />
			<span style={{ flex: 1 }}>{error}</span>
			{onRetry && (
				<button className="btn btn-secondary btn-sm" onClick={onRetry}>
					{t('error.retry')}
				</button>
			)}
		</div>
	);
}
