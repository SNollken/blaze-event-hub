import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Badge } from '../components/Badge';
import { StatsCard } from '../components/StatsCard';

describe('Badge', () => {
  it('defaults to neutral variant and renders children', () => {
    render(<Badge>rascunho</Badge>);
    const badge = screen.getByText('rascunho');
    expect(badge).toHaveClass('badge', 'badge-neutral');
  });

  it('applies the requested variant class', () => {
    render(<Badge variant="success">ativo</Badge>);
    expect(screen.getByText('ativo')).toHaveClass('badge-success');
  });

  it('renders an aria-hidden status dot mapped per variant', () => {
    const cases: Array<['success' | 'error' | 'warning' | 'neutral', string]> = [
      ['success', 'active'],
      ['error', 'error'],
      ['warning', 'warning'],
      ['neutral', 'inactive'],
    ];
    for (const [variant, dotClass] of cases) {
      const { container, unmount } = render(
        <Badge variant={variant} dot>
          x
        </Badge>,
      );
      const dot = container.querySelector('.status-dot');
      expect(dot, `variant ${variant}`).not.toBeNull();
      expect(dot).toHaveClass(dotClass);
      expect(dot).toHaveAttribute('aria-hidden', 'true');
      unmount();
    }
  });

  it('omits the dot by default and merges className/style', () => {
    const { container } = render(
      <Badge className="extra" style={{ marginInlineEnd: 4 }}>
        y
      </Badge>,
    );
    expect(container.querySelector('.status-dot')).toBeNull();
    const badge = screen.getByText('y');
    expect(badge).toHaveClass('badge-neutral', 'extra');
    expect(badge).toHaveStyle({ marginInlineEnd: '4px' });
  });
});

describe('StatsCard', () => {
  it('renders title and value', () => {
    render(<StatsCard title="Sorteios ativos" value={7} />);
    expect(screen.getByText('Sorteios ativos')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('renders the icon aria-hidden with the color class (default primary)', () => {
    const { container } = render(<StatsCard title="t" value="v" icon={<svg data-testid="ico" />} />);
    const wrap = container.querySelector('.text-primary');
    expect(wrap).not.toBeNull();
    expect(wrap).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByTestId('ico')).toBeInTheDocument();
  });

  it('maps explicit colors and omits the icon slot when absent', () => {
    const { container } = render(<StatsCard title="t" value="v" color="warning" icon={<svg />} />);
    expect(container.querySelector('.text-warning')).not.toBeNull();
    expect(container.querySelector('.text-primary')).toBeNull();

    const { container: noIcon } = render(<StatsCard title="t2" value="v2" />);
    expect(noIcon.querySelector('[aria-hidden]')).toBeNull();
  });
});
