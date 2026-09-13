/**
 * The wire shapes the account screens exchange.
 *
 * Each mirrors a record the backend already returns: `SessionResponse` in the
 * `auth` module and `UserResponse` in `users`.
 *
 * Nothing here is workspace-scoped, and that is the defining property of this
 * feature. An account belongs to a person rather than to a tenant, every
 * endpoint behind these screens is gated on `isAuthenticated()` and nothing
 * else, and the caller is always taken from the security context rather than
 * from a path — so there is no shape of request that reads or ends somebody
 * else's session.
 */

/**
 * One live session of the signed-in account.
 *
 * The address and the browser string are here because a list of sessions
 * without them tells nobody anything. They are shown only to their owner.
 *
 * `current` is the server's answer, computed from the refresh cookie the
 * request arrived with. It is not inferred here: the browser cannot read that
 * cookie, so this is the only way to know which row is the one you are sitting
 * in front of.
 */
export interface Session {
  sessionId: string
  userAgent: string | null
  ipAddress: string | null
  startedAt: string
  expiresAt: string
  current: boolean
}

/** The body of `PATCH /users/me`. Names only: an address is an account's identity. */
export interface UpdateProfileInput {
  firstName: string
  lastName: string
}

/** What `PATCH /users/me` answers with. */
export interface AccountProfile {
  id: string
  email: string
  firstName: string
  lastName: string
  status: string
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string
}
