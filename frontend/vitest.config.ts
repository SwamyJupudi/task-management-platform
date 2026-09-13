import path from 'node:path'

import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/**
 * The test configuration, kept apart from `vite.config.ts`.
 *
 * Separate rather than a `test` block inside the build config, because that one
 * is a function of `mode` that reads the environment and declares a dev server
 * and a proxy — none of which a test run has any business evaluating. This
 * declares only what the tests need: the React transform and the `@` alias,
 * both of which have to match the application's or an import would resolve to a
 * different module under test than in the browser.
 *
 * The environment is `node` by default and `jsdom` only where a test says so
 * with a `@vitest-environment jsdom` docblock. Standing jsdom up dominated the
 * first run of the suite, and the great majority of these tests are pure
 * functions that never touch a document. jsdom rather than happy-dom where it
 * is needed: the API client uses `AbortSignal.any` and `AbortSignal.timeout`,
 * and jsdom is the more faithful of the two about them.
 *
 * `globals` is on so `describe` and `it` need no import in every file, which is
 * the shape `tsconfig.app.json` is told about through `vitest/globals`.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // Generated shadcn components are vendored code and are not this suite's
    // to assert about.
    exclude: ['node_modules', 'dist', 'src/components/ui/**'],
    restoreMocks: true,
  },
})
