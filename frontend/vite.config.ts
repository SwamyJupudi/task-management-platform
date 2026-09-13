import path from 'node:path'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Only VITE_-prefixed values reach the client bundle; this reads the same
  // set so the dev proxy can be pointed at a non-default backend.
  const env = loadEnv(mode, process.cwd(), 'VITE_')

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
    server: {
      // The backend's CORS_ALLOWED_ORIGINS defaults to this exact origin.
      port: 5173,
      strictPort: true,
      proxy: {
        // Optional: lets the app run same-origin in development, so the
        // refresh cookie needs no cross-site handling. Unused when
        // VITE_API_BASE_URL points straight at the backend.
        '/api': {
          target: env.VITE_DEV_PROXY_TARGET ?? 'http://localhost:8081',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
    },
  }
})
