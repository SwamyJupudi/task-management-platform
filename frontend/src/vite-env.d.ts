/// <reference types="vite/client" />

/**
 * The contract for `.env` files. Adding a variable here and to `.env.example`
 * is what makes it visible to `src/config/env.ts`.
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_NAME?: string
  readonly VITE_API_TIMEOUT_MS?: string
  readonly VITE_ENABLE_QUERY_DEVTOOLS?: string
  readonly VITE_DEV_PROXY_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
