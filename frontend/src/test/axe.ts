import { configureAxe } from 'vitest-axe';

/**
 * axe-core runner configured for jsdom.
 *
 * color-contrast is disabled because jsdom performs no layout/paint, so axe
 * cannot compute rendered contrast there. Contrast is validated per design
 * token (tokens.css pairs) instead of via jsdom axe runs.
 */
export const axe = configureAxe({
  rules: {
    'color-contrast': { enabled: false },
  },
});
