/**
 * The wire shapes the authentication endpoints exchange.
 *
 * Each mirrors a record in the Spring `auth.dto` package. The request shapes
 * are derived from the Zod schemas in `schemas.ts` rather than declared twice,
 * so a field can only drift in one place.
 */

import type { User } from '@/types/session'

/**
 * The body of `POST /auth/login`, `/auth/refresh` and `/auth/password/change`.
 *
 * There is no refresh token in here on purpose. It travels in an httpOnly
 * cookie the browser will not let a script read, and a copy in the body would
 * undo exactly the protection that buys.
 */
export interface AuthTokens {
  accessToken: string
  /** Always `Bearer`. Kept because the backend sends it. */
  tokenType: string
  /** Seconds until `accessToken` expires. Drives the pre-emptive renewal. */
  expiresIn: number
  user: AccountUser
}

/**
 * `UserResponse`, which carries three fields beyond the shared `User` the
 * layout reads. `emailVerified` is the one the sign-in screen needs, to tell
 * an unverified address apart from a wrong password.
 */
export interface AccountUser extends User {
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string
}

/** The body of `GET /workspaces/{workspaceId}/me`. */
export interface WorkspacePermissionsBody {
  workspaceId: string
  permissions: string[]
}

