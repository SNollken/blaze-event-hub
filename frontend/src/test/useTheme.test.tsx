import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTheme, initTheme } from '../hooks/useTheme';

const STORAGE_KEY = 'beh-theme';

type MediaHandler = () => void;

let mediaMatches = false;
let registeredHandlers: MediaHandler[] = [];

function installMatchMediaMock() {
  const mock = vi.fn().mockImplementation(() => ({
    matches: mediaMatches,
    media: '(prefers-color-scheme: light)',
    addEventListener: (_event: string, handler: MediaHandler) => {
      registeredHandlers.push(handler);
    },
    removeEventListener: (_event: string, handler: MediaHandler) => {
      registeredHandlers = registeredHandlers.filter((h) => h !== handler);
    },
  }));
  vi.stubGlobal('matchMedia', mock);
  return mock;
}

function fireSystemThemeChange(matches: boolean) {
  mediaMatches = matches;
  // re-install so getSystemTheme() inside the handler sees the new value
  installMatchMediaMock();
  for (const h of [...registeredHandlers]) h();
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    mediaMatches = false;
    registeredHandlers = [];
    installMatchMediaMock();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('defaults to dark when nothing is stored (streamer tool default)', () => {
    const { result } = renderHook(() => useTheme());
    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('persists the theme choice to localStorage on mount', () => {
    renderHook(() => useTheme());
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
  });

  it('toggleTheme flips dark -> light -> dark, class and storage follow', () => {
    const { result } = renderHook(() => useTheme());

    act(() => result.current.toggleTheme());
    expect(result.current.theme).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light');

    act(() => result.current.toggleTheme());
    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
  });

  it('restores a stored light theme on first render', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    const { result } = renderHook(() => useTheme());
    expect(result.current.theme).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('ignores invalid stored values and falls back to dark', () => {
    localStorage.setItem(STORAGE_KEY, 'neon');
    const { result } = renderHook(() => useTheme());
    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('unmount detaches the system theme listener', () => {
    const { unmount } = renderHook(() => useTheme());
    expect(registeredHandlers.length).toBe(1);
    unmount();
    expect(registeredHandlers.length).toBe(0);
  });

  it('follows system preference changes only while no explicit choice is stored', () => {
    const { result } = renderHook(() => useTheme());

    // mount persists the default; simulate an external clear of the key
    localStorage.removeItem(STORAGE_KEY);

    act(() => fireSystemThemeChange(true));
    expect(result.current.theme).toBe('light');

    // now the user makes an explicit choice...
    act(() => result.current.toggleTheme());
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');

    // ...and system changes no longer override it
    act(() => fireSystemThemeChange(true));
    expect(result.current.theme).toBe('dark');
  });
});

describe('initTheme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('applies dark by default and does not persist (choice stays implicit)', () => {
    initTheme();
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('applies a stored light theme', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    initTheme();
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('ignores invalid stored values', () => {
    localStorage.setItem(STORAGE_KEY, 'rainbow');
    initTheme();
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });
});
