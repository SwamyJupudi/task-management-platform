import { configureApiClient } from '@/lib/api'
import { useSessionStore } from '@/stores/session-store'

import * as authApi from './api'
import type { AuthTokens } from './types'

/**
 * The session lifecycle, outside React.
 *
 * The access token is held in memory, in the Zustand store, and nowhere else.
 * Nothing writes it to `localStorage` or `sessionStorage`, where any injected
 * script could read it. The long-lived credential is the refresh cookie, which
 * is `HttpOnly`, `Secure`, `SameSite=Strict` and scoped to `/api/v1/auth`, so
 * JavaScript cannot reach it and it is not attached to ordinary requests.
 *
 * The consequence of holding the token in memory is that a reload loses it.
 * `bootstrapSession` is what makes that invisible: on start-up it spends the
 * cookie once for a fresh token and then asks who the caller is.
 *
 * This module is deliberately not a hook. The API client has to be able to
 * renew a session from inside a failed request, which happens nowhere near a
 * React render, and the route guards read the store without importing any of
 * this.
 */

/** Renew this many seconds before the token actually expires. */
const RENEWAL_MARGIN_SECONDS = 60

/** Never schedule a renewal tighter than this, however short the token's life. */
const MINIMUM_RENEWAL_DELAY_MS = 5_000

let renewalTimer: ReturnType<typeof setTimeout> | undefined

/**
 * One refresh at a time.
 *
 * The API client already de-duplicates the refreshes it starts itself, but
 * bootstrap and the renewal timer call in here directly, so the guard has to
 * live at this level too. Rotation means a second concurrent refresh would
 * present an already-spent token, which the reuse guard treats as theft and
 * answers by ending every session.
 */
let inFlight: Promise<boolean> | null = null

/** True once bootstrap has run, so a remount does not spend the cookie again. */
let bootstrapped: Promise<void> | null = null

/**
 * Which session the in-flight work belongs to. Bumped by every `endSession`.
 *
 * Ending a session cannot cancel a request that is already on the wire, so the
 * result of one still arrives afterwards. Without this, a refresh that was in
 * flight when somebody signed out would resolve into the store they had just
 * emptied, putting a live access token back in memory and starting a fresh
 * renewal timer for a session that no longer exists.
 *
 * Every operation that writes to the store after an `await` records the
 * generation it started in and drops its result if that has moved on.
 */
let generation = 0

function cancelRenewal(): void {
  if (renewalTimer !== undefined) {
    clearTimeout(renewalTimer)
    renewalTimer = undefined
  }
}

/**
 * Renews shortly before expiry rather than waiting for a 401.
 *
 * The client's retry-on-401 is the safety net and stays; this is what keeps a
 * tab that has been open for an hour from paying a failed request first.
 */
function scheduleRenewal(expiresInSeconds: number): void {
  cancelRenewal()
  const delayMs = Math.max(
    (expiresInSeconds - RENEWAL_MARGIN_SECONDS) * 1000,
    MINIMUM_RENEWAL_DELAY_MS,
  )
  renewalTimer = setTimeout(() => {
    void refreshSession()
  }, delayMs)
}

/** Records a freshly issued token and arranges for its replacement. */
function acceptTokens(tokens: AuthTokens): void {
  useSessionStore.getState().setAccessToken(tokens.accessToken)
  scheduleRenewal(tokens.expiresIn)
}

/**
 * Loads the profile, platform role, permissions and memberships behind the
 * current token, and marks the session authenticated.
 */
async function loadCurrentUser(): Promise<void> {
  const observed = generation
  const current = await authApi.me()
  if (observed !== generation) return
  useSessionStore.getState().setCurrentUser(current)
}

/**
 * Puts a completed sign-in into effect.
 *
 * Two calls rather than one because `/auth/login` returns the account but not
 * the permissions or the memberships, which is the same split `/auth/me`
 * exists to serve. Deliberately not merged: one shape for "who is signed in"
 * means the reload path and the sign-in path populate the store identically.
 */
export async function establishSession(tokens: AuthTokens): Promise<void> {
  acceptTokens(tokens)
  await loadCurrentUser()
}

/**
 * Spends the refresh cookie for a new access token.
 *
 * Resolves false rather than throwing when there is no usable cookie, because
 * every caller treats that as "not signed in" and not as a failure: on
 * start-up it is the ordinary state of a first visit.
 */
export function refreshSession(): Promise<boolean> {
  const observed = generation

  inFlight ??= authApi
    .refresh()
    .then((tokens) => {
      // The session this was started for has since ended. The token that came
      // back is real, but nobody is waiting for it: taking it would re-arm a
      // session the user asked to leave.
      if (observed !== generation) return false
      acceptTokens(tokens)
      return true
    })
    .catch(() => {
      cancelRenewal()
      return false
    })
    .finally(() => {
      inFlight = null
    })

  return inFlight
}

/**
 * Decides, once per page load, whether this browser already has a session.
 *
 * The store starts at `unknown` and the guards hold at a spinner until this
 * resolves, which is what stops a signed-in user being bounced to the sign-in
 * screen on every reload.
 *
 * Memoised because React's StrictMode mounts effects twice in development, and
 * spending a rotating refresh token twice would revoke the session it was
 * trying to restore.
 */
export function bootstrapSession(): Promise<void> {
  bootstrapped ??= (async () => {
    const renewed = await refreshSession()
    if (!renewed) {
      useSessionStore.getState().markAnonymous()
      return
    }
    try {
      await loadCurrentUser()
    } catch {
      // The token is good but the account behind it cannot be described:
      // deactivated between sessions, or deleted. Treat it as no session
      // rather than leaving the app stuck at `unknown` forever.
      endSession()
    }
  })()

  return bootstrapped
}

/**
 * Drops the session locally.
 *
 * Local only: revoking the refresh token server-side is `POST /auth/logout`,
 * which `useLogout` calls first. This also runs when a refresh fails, where
 * there is nothing left to revoke.
 */
export function endSession(): void {
  cancelRenewal()
  // Anything still on the wire now belongs to a session that is over, and will
  // discard its own result when it lands.
  generation += 1
  // `inFlight` is deliberately left alone. It clears itself when the request
  // settles, and clearing it here would let a second refresh start while the
  // first is still running — two presentations of a rotating token, which the
  // reuse guard treats as theft.
  bootstrapped = Promise.resolve()
  useSessionStore.getState().clear()
}

/**
 * Fills in the two seams the API client was built with.
 *
 * Called once from `main.tsx`. Until it runs, a 401 surfaces as an `ApiError`
 * and nothing tries to renew, which is the scaffold behaviour.
 */
export function installSessionManager(): void {
  configureApiClient({
    refreshToken: refreshSession,
    onSessionExpired: endSession,
  })
}
