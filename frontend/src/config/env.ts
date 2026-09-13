/**
 * The typed, validated view of the environment.
 *
 * Vite exposes only `VITE_`-prefixed values to the bundle, so nothing secret
 * can arrive here by accident. Everything the application reads is resolved
 * once, at module load, and a missing or malformed value fails loudly at
 * start-up rather than as an undefined deep inside a request.
 */

type RawEnv = ImportMetaEnv

function required(raw: RawEnv, key: keyof ImportMetaEnv & string): string {
  const value = raw[key]
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(
      `Missing required environment variable ${key}. ` +
        'Copy .env.example to .env.local and fill it in.',
    )
  }
  return value.trim()
}

function optionalBoolean(raw: RawEnv, key: keyof ImportMetaEnv & string, fallback: boolean): boolean {
  const value = raw[key]
  if (value === undefined || value === '') return fallback
  return value === 'true'
}

function optionalNumber(raw: RawEnv, key: keyof ImportMetaEnv & string, fallback: number): number {
  const value = raw[key]
  if (value === undefined || value === '') return fallback
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error(`Environment variable ${key} must be a number, got "${value}".`)
  }
  return parsed
}

/** Trailing slashes are stripped so path joining never produces a double slash. */
function normaliseBaseUrl(value: string): string {
  return value.replace(/\/+$/, '')
}

function read(raw: RawEnv) {
  return {
    /** Where the Spring backend lives, including its `/api/v1` base path. */
    apiBaseUrl: normaliseBaseUrl(required(raw, 'VITE_API_BASE_URL')),
    /** Shown in the document title and the shell header. */
    appName: raw.VITE_APP_NAME?.trim() || 'Task Management Platform',
    /** How long a request may run before the client gives up, in milliseconds. */
    requestTimeoutMs: optionalNumber(raw, 'VITE_API_TIMEOUT_MS', 30_000),
    /** Turns on the TanStack Query devtools panel. Off unless asked for. */
    enableQueryDevtools: optionalBoolean(raw, 'VITE_ENABLE_QUERY_DEVTOOLS', false),
    /** True only in `vite build`, so guards can be stricter in production. */
    isProduction: raw.PROD,
    mode: raw.MODE,
  } as const
}

export type Env = ReturnType<typeof read>

export const env: Env = read(import.meta.env)
