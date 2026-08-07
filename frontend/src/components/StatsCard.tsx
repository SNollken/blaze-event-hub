import { ReactNode } from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

interface StatsCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon?: ReactNode;
  trend?: 'up' | 'down' | 'neutral';
  trendValue?: string;
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

export function StatsCard({ title, value, subtitle, icon, trend, trendValue, color = 'primary' }: StatsCardProps) {
  const TrendIcon = trend === 'up' ? TrendingUp : trend === 'down' ? TrendingDown : Minus;

  return (
    <div className="glass-card p-5 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-text-secondary">{title}</span>
        {icon && <span className={`${colorClass[color]} opacity-70`} aria-hidden="true">{icon}</span>}
      </div>
      <div className="text-[28px] font-bold text-text-primary leading-[1.1]">
        {value}
      </div>
      <div className="flex items-center gap-1.5">
        {trend && trendValue && (
          <span className={`inline-flex items-center gap-0.5 text-xs font-medium ${
            trend === 'up' ? 'text-success' : trend === 'down' ? 'text-error' : 'text-text-muted'
          }`}>
            <TrendIcon size={12} />
            {trendValue}
          </span>
        )}
        {subtitle && (
          <span className="text-xs text-text-muted">{subtitle}</span>
        )}
      </div>
    </div>
  );
}
