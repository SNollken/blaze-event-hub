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
      {dot && <span className={`status-dot ${variant === 'success' ? 'active' : variant === 'error' ? 'error' : variant === 'warning' ? 'warning' : 'inactive'} mr-1`} aria-hidden="true" />}
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
    <span className="inline-flex items-center gap-1.5 text-[13px]">
      <span className={`status-dot ${status}`} aria-hidden="true" />
      {label && <span className="text-text-secondary">{label}</span>}
    </span>
  );
}
