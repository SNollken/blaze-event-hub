import { ReactNode } from 'react';

type BadgeVariant = 'success' | 'error' | 'warning' | 'neutral';

interface BadgeProps {
  children: ReactNode;
  variant?: BadgeVariant;
  dot?: boolean;
  style?: React.CSSProperties;
}

export function Badge({ children, variant = 'neutral', dot = false, style }: BadgeProps) {
  return (
    <span className={`badge badge-${variant}`} style={style}>
      {dot && <span className={`status-dot ${variant === 'success' ? 'active' : variant === 'error' ? 'error' : variant === 'warning' ? 'warning' : 'inactive'}`} style={{ marginRight: 4 }} />}
      {children}
    </span>
  );
}

interface StatusDotProps {
  status: 'active' | 'inactive' | 'error' | 'warning';
  label?: string;
}

export function StatusDot({ status, label }: StatusDotProps) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
      <span className={`status-dot ${status}`} />
      {label && <span style={{ color: 'var(--text-secondary)' }}>{label}</span>}
    </span>
  );
}
