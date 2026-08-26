import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * WCAG 2.2 AA contrast audit of the design tokens themselves.
 *
 * axe-core cannot check color-contrast under jsdom (no layout), so the token
 * palette is audited mechanically here: every foreground/background pair that
 * the components actually use must meet the AA threshold.
 *
 * Thresholds (WCAG 1.4.3 text, 1.4.11 non-text):
 *  - body/UI text:            4.5:1
 *  - UI component boundaries: 3:1 (input borders, focus rings)
 */

const css = readFileSync(resolve(__dirname, '../styles/tokens.css'), 'utf-8');

type Palette = Record<string, string>;

function parseBlock(source: string, marker: string): Palette {
  const start = source.indexOf(marker);
  if (start < 0) throw new Error(`tokens.css: marker not found: ${marker}`);
  const end = source.indexOf('\n}', start);
  if (end < 0) throw new Error(`tokens.css: unterminated block for ${marker}`);
  const block = source.slice(start, end);
  const palette: Palette = {};
  for (const m of block.matchAll(/--(color-[a-z-]+):\s*(#[0-9a-fA-F]{6})/g)) {
    palette[m[1]] = m[2].toUpperCase();
  }
  return palette;
}

// Dark palette = @theme defaults; light = :root:not(.dark) overrides on top.
const dark: Palette = parseBlock(css, '@theme {');
const light: Palette = { ...dark, ...parseBlock(css, ':root:not(.dark) {') };

function linear(channel: number): number {
  const c = channel / 255;
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function luminance(hex: string): number {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

function contrast(fg: string, bg: string): number {
  const lf = luminance(fg);
  const lb = luminance(bg);
  const hi = Math.max(lf, lb);
  const lo = Math.min(lf, lb);
  return (hi + 0.05) / (lo + 0.05);
}

const TEXT = ['color-text-primary', 'color-text-secondary', 'color-text-muted'];
const SURFACES = [
  'color-bg-base',
  'color-bg-elevated',
  'color-bg-card',
  'color-bg-input',
  'color-bg-sidebar',
];
const COLORED_TEXT_ON_CARD = [
  'color-primary',
  'color-accent',
  'color-success',
  'color-error',
  'color-warning',
];
const BUTTON_FILLS = ['color-primary', 'color-primary-hover', 'color-accent'];

for (const [name, palette] of [['dark', dark], ['light', light]] as const) {
  describe(`tokens.css contrast — ${name} theme (WCAG 2.2 AA)`, () => {
    it.each(
      TEXT.flatMap((fg) => SURFACES.map((bg) => [fg, bg] as const)),
    )('text %s on %s >= 4.5:1', (fg, bg) => {
      const ratio = contrast(palette[fg], palette[bg]);
      expect(
        ratio,
        `${fg} (${palette[fg]}) on ${bg} (${palette[bg]}) = ${ratio.toFixed(2)}:1`,
      ).toBeGreaterThanOrEqual(4.5);
    });

    it.each(COLORED_TEXT_ON_CARD)(
      'colored text/icon %s on card >= 4.5:1',
      (fg) => {
        const ratio = contrast(palette[fg], palette['color-bg-card']);
        expect(
          ratio,
          `${fg} (${palette[fg]}) on bg-card (${palette['color-bg-card']}) = ${ratio.toFixed(2)}:1`,
        ).toBeGreaterThanOrEqual(4.5);
      },
    );

    it.each(BUTTON_FILLS)(
      'button label (text-inverse) on fill %s >= 4.5:1',
      (bg) => {
        const ratio = contrast(palette['color-text-inverse'], palette[bg]);
        expect(
          ratio,
          `text-inverse (${palette['color-text-inverse']}) on ${bg} (${palette[bg]}) = ${ratio.toFixed(2)}:1`,
        ).toBeGreaterThanOrEqual(4.5);
      },
    );

    it('input/card border (border-default) visible on base and card >= 3:1', () => {
      for (const bg of ['color-bg-base', 'color-bg-card']) {
        const ratio = contrast(palette['color-border-default'], palette[bg]);
        expect(
          ratio,
          `border-default (${palette['color-border-default']}) on ${bg} (${palette[bg]}) = ${ratio.toFixed(2)}:1`,
        ).toBeGreaterThanOrEqual(3);
      }
    });

    it('focus ring (border-focus) visible on base >= 3:1', () => {
      const ratio = contrast(palette['color-border-focus'], palette['color-bg-base']);
      expect(
        ratio,
        `border-focus (${palette['color-border-focus']}) on bg-base (${palette['color-bg-base']}) = ${ratio.toFixed(2)}:1`,
      ).toBeGreaterThanOrEqual(3);
    });

    it('secondary button label (text-primary) on border-default fill >= 4.5:1', () => {
      const ratio = contrast(palette['color-text-primary'], palette['color-border-default']);
      expect(
        ratio,
        `text-primary (${palette['color-text-primary']}) on border-default (${palette['color-border-default']}) = ${ratio.toFixed(2)}:1`,
      ).toBeGreaterThanOrEqual(4.5);
    });
  });
}
