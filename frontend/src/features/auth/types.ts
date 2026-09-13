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

/**
 * `GET /invitations?token=…`, the public preview behind an invitation link.
 *
 * Deliberately thin, and that is the backend's decision rather than an
 * omission: anybody holding the link can read this without signing in, so it
 * names the workspace and the invited address and nothing that would describe
 * the workspace's people or its work.
 *
 * `accountExists` is what decides which of the two acceptance paths the screen
 * offers. It is the server's answer rather than something inferred from the
 * session, because the invited address and the signed-in one are not
 * necessarily the same person.
 */
export interface InvitationPreview {
  workspaceName: string
  /** The address the invitation was sent to. Not editable: it is the invitation. */
  email: string
  /** The role slug the invitation carries, e.g. `EMPLOYEE`. */
  roleSlug: string
  /** Whether an account already exists for `email`. */
  accountExists: boolean
}

/**
 * The body of `POST /invitations/accept`.
 *
 * Two shapes in one, matching `AcceptInvitationRequest`. Somebody who already
 * has an account sends the token alone and must be signed in as the invited
 * address. Somebody who does not sends a password and a name with it, and the
 * account is created and joined in one step — already verified, because the
 * token went to that address and nowhere else.
 */
export interface AcceptInvitationBody {
  token: string
  password?: string | undefined
  firstName?: string | undefined
  lastName?: string | undefined
}

/** What accepting returns: the workspace just joined, and nothing else. */
export interface AcceptedInvitation {
  workspaceId: string
}
