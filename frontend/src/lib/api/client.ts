import { env } from '@/config/env'

import { ApiError, NetworkError } from './error'
import type { ApiErrorBody } from './types'

/**
 * The single HTTP client every feature calls through.
 *
 * Uses `fetch` rather than a client library: the backend speaks plain JSON
 * with one error shape and one page shape, so there is nothing here a
 * dependency would earn its place doing.
 *
 * Two seams are deliberately left open and are filled in by the
 * authentication feature when it is built. Nothing in this file knows how a
 * session works, which is what keeps the transport free of feature
 * knowledge and free of an import cycle with the session store.
 */

/** Supplies the bearer token, or null when there is no session. */
export type TokenProvider = () => string | null

/**
 * Attempts to renew an expired session. Resolves true when a retry is worth
 * making. The refresh cookie is httpOnly, so the browser, not this module,
 * holds the credential.
 */
export type TokenRefresher = () => Promise<boolean>

/** Called when a session is past saving, so the app can return to sign-in. */
export type SessionExpiredHandler = () => void

let getToken: TokenProvider = () => null
let refreshToken: TokenRefresher | null = null
let onSessionExpired: SessionExpiredHandler | null = null

export function configureApiClient(options: {
  getToken?: TokenProvider
  refreshToken?: TokenRefresher
  onSessionExpired?: SessionExpiredHandler
}): void {
  if (options.getToken) getToken = options.getToken
  if (options.refreshToken) refreshToken = options.refreshToken
  if (options.onSessionExpired) onSessionExpired = options.onSessionExpired
}

export type QueryValue = string | number | boolean | null | undefined
export type QueryParams = Record<string, QueryValue | QueryValue[]>

export interface RequestOptions extends Omit<RequestInit, 'body' | 'method'> {
  /** Serialised as JSON unless it is already a `FormData` or `Blob`. */
  body?: unknown
  /** Appended to the URL. Null and undefined entries are dropped. */
  params?: QueryParams
  /** Skips the bearer header, for the few endpoints that take no session. */
  anonymous?: boolean
  /** Overrides the default timeout for this call. */
  timeoutMs?: number
}

function buildUrl(path: string, params?: QueryParams): string {
  const base = path.startsWith('http')
    ? path
    : `${env.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`
  if (!params) return base

  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined) continue
    if (Array.isArray(value)) {
      // Repeated keys, which is what Spring binds to a List parameter.
      for (const item of value) {
        if (item === null || item === undefined) continue
        search.append(key, String(item))
      }
    } else {
      search.append(key, String(value))
    }
  }

  const query = search.toString()
  return query ? `${base}?${query}` : base
}

function isRawBody(body: unknown): body is BodyInit {
  return body instanceof FormData || body instanceof Blob || body instanceof URLSearchParams
}

/** Reads the backend's error envelope, tolerating a response that is not one. */
async function toApiError(response: Response): Promise<ApiError> {
  const requestId = response.headers.get('X-Request-Id') ?? undefined
  let body: ApiErrorBody | undefined

  try {
    const parsed: unknown = await response.json()
    if (parsed && typeof parsed === 'object' && 'code' in parsed && 'message' in parsed) {
      body = parsed as ApiErrorBody
    }
  } catch {
    // A proxy or gateway can fail before the application is reached, in which
    // case the body is HTML or empty. Fall through to the generic message.
  }

  return new ApiError({
    status: response.status,
    code: body?.code ?? 'UNEXPECTED_ERROR',
    message: body?.message ?? 'Something went wrong. Please try again.',
    requestId: body?.requestId ?? requestId,
    violations: body?.errors ?? [],
    body,
  })
}

/**
 * One refresh at a time.
 *
 * Several queries commonly expire together; without this they would each fire
 * their own refresh and all but one would be rejected by rotation.
 */
let inFlightRefresh: Promise<boolean> | null = null

function refreshOnce(): Promise<boolean> {
  if (!refreshToken) return Promise.resolve(false)
  inFlightRefresh ??= refreshToken().finally(() => {
    inFlightRefresh = null
  })
  return inFlightRefresh
}

async function send(path: string, method: string, options: RequestOptions): Promise<Response> {
  const { body, params, anonymous, timeoutMs, headers, signal, ...rest } = options

  const requestHeaders = new Headers(headers)
  requestHeaders.set('Accept', 'application/json')

  let payload: BodyInit | undefined
  if (body !== undefined) {
    if (isRawBody(body)) {
      // The browser sets the multipart boundary; setting it here breaks it.
      payload = body
    } else {
      requestHeaders.set('Content-Type', 'application/json')
      payload = JSON.stringify(body)
    }
  }

  if (!anonymous) {
    const token = getToken()
    if (token) requestHeaders.set('Authorization', `Bearer ${token}`)
  }

  const timeout = AbortSignal.timeout(timeoutMs ?? env.requestTimeoutMs)
  const composedSignal = signal ? AbortSignal.any([signal, timeout]) : timeout

  try {
    return await fetch(buildUrl(path, params), {
      ...rest,
      method,
      headers: requestHeaders,
      // Carries the httpOnly refresh cookie. The backend sets
      // app.cors.allow-credentials=true for exactly this.
      credentials: 'include',
      signal: composedSignal,
      ...(payload === undefined ? {} : { body: payload }),
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    if (timeout.aborted) throw new NetworkError('The request timed out.', cause)
    throw new NetworkError('The request could not be sent.', cause)
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }
  const text = await response.text()
  if (text === '') return undefined as T
  return JSON.parse(text) as T
}

/**
 * Sends, renews a dead session once, and turns a failure into an `ApiError`.
 *
 * Everything that talks to the API funnels through here, whether it wants the
 * body parsed or the response itself. Keeping the retry in one place is what
 * stops a second way of calling the API from quietly missing it.
 */
async function exchange(path: string, method: string, options: RequestOptions): Promise<Response> {
  let response = await send(path, method, options)

  // One retry, and only for an expired session on a call that carried one.
  if (response.status === 401 && !options.anonymous) {
    const renewed = await refreshOnce()
    if (renewed) {
      response = await send(path, method, options)
    } else {
      onSessionExpired?.()
    }
  }

  if (!response.ok) throw await toApiError(response)
  return response
}

async function request<T>(path: string, method: string, options: RequestOptions = {}): Promise<T> {
  return parse<T>(await exchange(path, method, options))
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, 'GET', options ?? {}),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, 'POST', { ...options, ...(body === undefined ? {} : { body }) }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, 'PUT', { ...options, ...(body === undefined ? {} : { body }) }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, 'PATCH', { ...options, ...(body === undefined ? {} : { body }) }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, 'DELETE', options ?? {}),

  /**
   * The response itself, for the few endpoints that do not answer JSON.
   *
   * A file download is the case this exists for. It carries the bearer token
   * like every other call, which a plain anchor could not, and it goes through
   * the same retry-on-401 as everything else rather than being a second way
   * into the API that quietly misses it.
   *
   * The caller reads the body; nothing here parses it.
   */
  raw: (path: string, options?: RequestOptions) => exchange(path, 'GET', options ?? {}),
} as const
