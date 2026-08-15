import { ReactNode } from 'react';

interface StatsCardProps {
  title: string;
  value: string | number;
  icon?: ReactNode;
  color?: 'primary' | 'accent' | 'success' | 'error' | 'warning' | 'neutral';
}

const colorClass = {
  primary: 'text-primary',
  accent: 'text-accent',
  success: 'text-success',
  error: 'text-error',
  warning: 'text-warning',
  neutral: 'text-text-muted',
} as const;

export function StatsCard({ title, value, icon, color = 'primary' }: StatsCardProps) {
  return (
    <div className="glass-card p-5 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-text-secondary">{title}</span>
        {icon && <span className={`${colorClass[color]} opacity-70`} aria-hidden="true">{icon}</span>}
      </div>
      <div className="text-[28px] font-bold text-text-primary leading-[1.1]">
        {value}
      </div>
    </div>
  );
}
