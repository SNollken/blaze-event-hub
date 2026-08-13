import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    css: true,
    // Sequential file execution: findBy* queries rely on real timers and the
    // default 1s async timeout; parallel jsdom workers on a small VPS starve
    // the event loop and make smoke tests flaky. Reliability > wall-clock here.
    fileParallelism: false,
  },
})