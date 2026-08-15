import { ReactNode } from 'react';

type BadgeVariant = 'success' | 'error' | 'warning' | 'neutral';

interface BadgeProps {
  children: ReactNode;
  variant?: BadgeVariant;
  dot?: boolean;
  className?: string;
  style?: React.CSSProperties;
}

export function Badge({ children, variant = 'neutral', dot = false, className, style }: BadgeProps) {
  return (
    <span className={`badge badge-${variant} ${className || ''}`} style={style}>
      {dot && <span className={`status-dot ${variant === 'success' ? 'active' : variant === 'error' ? 'error' : variant === 'warning' ? 'warning' : 'inactive'} mr-1`} aria-hidden="true" />}
      {children}
    </span>
  );
}

