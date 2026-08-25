import 'vitest';

/**
 * Typing for the vitest-axe `toHaveNoViolations` matcher registered in
 * setup.ts. vitest-axe ships an older Vi-namespace augmentation that does
 * not apply to vitest 4, so the module augmentation lives here.
 */
declare module 'vitest' {
  interface Assertion<T = any> {
    toHaveNoViolations(): T;
  }
  interface AsymmetricMatchersContaining {
    toHaveNoViolations(): void;
  }
}
