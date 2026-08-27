import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

// Lint layer introduced in round r68: eslint js recommended + TS recommended
// + react-hooks. Deliberately NOT type-checked (recommendedTypeChecked) to
// keep the signal/noise ratio high; harden later if the codebase stays clean.
export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.node },
    },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
    },
  },
  {
    files: ['eslint.config.js', 'vite.config.ts'],
    languageOptions: { globals: globals.node },
  },
  {
    // Ambient/declaration files: vitest-axe.d.ts must declare
    // `Assertion<T = any>` to merge with vitest's own declaration
    // (identical type parameters are required), so `any` is not
    // negotiable there.
    files: ['**/*.d.ts'],
    rules: { '@typescript-eslint/no-explicit-any': 'off' },
  },
);
