import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // Dev-only: forward API calls to the Spring Boot backend (port 8080)
      // so `npm run dev` works without CORS configuration.
      '/api': 'http://localhost:8080',
    },
  },
})
