import { api } from '@/lib/api'

import type { AccountProfile, Session, UpdateProfileInput } from './types'

/**
 * The account endpoints this feature owns.
 *
 * Deliberately short. The two calls that mint or destroy tokens — changing a
 * password and signing out everywhere — live in the authentication feature
 * instead, because putting a new token pair into effect is its job and a second
 * place that did it would be a second place to get it wrong.
 *
 * None of these takes an identifier. `PATCH /users/me` and `GET /auth/sessions`
 * both read the caller from the security context, which is what makes it
 * impossible to address somebody else's account through them.
 */

/**
 * `PATCH /users/me`. The caller's own names.
 *
 * A different endpoint from the administrative `PATCH /users/{id}`, and not by
 * accident: renaming yourself is not an administrative action and is not
 * recorded as one.
 *
 * The address is absent because the backend has no field for it here. It is the
 * account's identity, changing it needs re-verification and a decision about
 * live sessions, and none of that exists.
 */
export function updateOwnProfile(body: UpdateProfileInput): Promise<AccountProfile> {
  return api.patch<AccountProfile>('/users/me', body)
}

/**
 * `GET /auth/sessions`. Every live session of the caller.
 *
 * The request carries the refresh cookie, which is how the backend marks one
 * row `current`. Nothing here can work that out on its own — the cookie is
 * httpOnly by design.
 */
export function listSessions(): Promise<Session[]> {
  return api.get<Session[]>('/auth/sessions')
}

/**
 * `DELETE /auth/sessions/{sessionId}`.
 *
 * Scoped to the caller server-side, so a session identifier belonging to
 * somebody else is simply not found rather than refused.
 */
export function revokeSession(sessionId: string): Promise<void> {
  return api.delete<void>(`/auth/sessions/${sessionId}`)
}
