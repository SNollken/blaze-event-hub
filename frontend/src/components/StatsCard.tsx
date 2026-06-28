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

const colorMap = {
  primary: 'var(--primary)',
  accent: 'var(--accent)',
  success: 'var(--success)',
  error: 'var(--error)',
  warning: 'var(--warning)',
  neutral: 'var(--text-muted)',
};

export function StatsCard({ title, value, subtitle, icon, trend, trendValue, color = 'primary' }: StatsCardProps) {
  const TrendIcon = trend === 'up' ? TrendingUp : trend === 'down' ? TrendingDown : Minus;
  const trendColor = trend === 'up' ? 'var(--success)' : trend === 'down' ? 'var(--error)' : 'var(--text-muted)';

  return (
    <div className="glass-card" style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)' }}>{title}</span>
        {icon && <span style={{ color: colorMap[color], opacity: 0.7 }}>{icon}</span>}
      </div>
      <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--text-primary)', lineHeight: 1.1 }}>
        {value}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {trend && trendValue && (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 12, color: trendColor, fontWeight: 500 }}>
            <TrendIcon size={12} />
            {trendValue}
          </span>
        )}
        {subtitle && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{subtitle}</span>
        )}
      </div>
    </div>
  );
}
