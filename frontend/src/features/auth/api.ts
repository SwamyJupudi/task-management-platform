import { api } from '@/lib/api'
import type { CurrentUser } from '@/types/session'

import type {
  AcceptedInvitation,
  AcceptInvitationBody,
  AccountUser,
  AuthTokens,
  InvitationPreview,
  WorkspacePermissionsBody,
} from './types'

/**
 * Every authentication endpoint, in one place.
 *
 * Thin on purpose: each function is a path, a body and a return type, and the
 * transport, the error envelope and the bearer header are all the shared
 * client's business. Nothing here decides what to do with a result — that is
 * the hooks' job, so these stay usable outside React.
 *
 * `anonymous: true` marks the calls that must not carry a bearer token. It
 * matters most on `refresh`: the client retries a 401 by refreshing, so a
 * refresh that could itself trigger a refresh would recurse.
 */

/** `POST /auth/register`. Creates a person and mails them a verification link. */
export function register(body: {
  email: string
  password: string
  firstName: string
  lastName: string
}): Promise<AccountUser> {
  return api.post<AccountUser>('/auth/register', body, { anonymous: true })
}

/** `POST /auth/verify-email`. The token comes from the mailed link's query string. */
export function verifyEmail(token: string): Promise<void> {
  return api.post<void>('/auth/verify-email', { token }, { anonymous: true })
}

/**
 * `POST /auth/verify-email/resend`.
 *
 * Answers 202 whether or not the address is registered, so a caller learns
 * nothing from the response and neither does this function.
 */
export function resendVerification(email: string): Promise<void> {
  return api.post<void>('/auth/verify-email/resend', { email }, { anonymous: true })
}

/** `POST /auth/login`. Returns the access token and sets the refresh cookie. */
export function login(body: { email: string; password: string }): Promise<AuthTokens> {
  return api.post<AuthTokens>('/auth/login', body, { anonymous: true })
}

/**
 * `POST /auth/refresh`. Exchanges the refresh cookie for a new access token
 * and rotates the cookie.
 *
 * Takes no body: the credential is the httpOnly cookie, which the browser
 * attaches because the client sends `credentials: 'include'`.
 */
export function refresh(): Promise<AuthTokens> {
  return api.post<AuthTokens>('/auth/refresh', undefined, { anonymous: true })
}

/**
 * `POST /auth/logout`. Revokes the refresh token and clears the cookie.
 *
 * Anonymous because it is permitted without a session and needs only the
 * cookie. The access token is not revoked by it and simply expires.
 */
export function logout(): Promise<void> {
  return api.post<void>('/auth/logout', undefined, { anonymous: true })
}

/** `GET /auth/me`. Profile, platform role and permissions, and memberships. */
export function me(): Promise<CurrentUser> {
  return api.get<CurrentUser>('/auth/me')
}

/**
 * `POST /auth/password/forgot`.
 *
 * Always 202, so the response cannot be used to test whether an address is
 * registered. The screen shows the same confirmation either way.
 */
export function forgotPassword(email: string): Promise<void> {
  return api.post<void>('/auth/password/forgot', { email }, { anonymous: true })
}

/**
 * `POST /auth/password/reset`. Ends every session, including any this browser
 * holds, which is why the caller signs in again afterwards.
 */
export function resetPassword(body: { token: string; newPassword: string }): Promise<void> {
  return api.post<void>('/auth/password/reset', body, { anonymous: true })
}

/**
 * `GET /workspaces/{workspaceId}/me`. The caller's own permission codes in one
 * workspace.
 *
 * Lives here rather than in a workspaces feature because the route guards read
 * it and they must not depend on a feature folder. It needs membership and no
 * permission code, so an employee can ask what they may do.
 */
export function workspacePermissions(workspaceId: string): Promise<WorkspacePermissionsBody> {
  return api.get<WorkspacePermissionsBody>(`/workspaces/${workspaceId}/me`)
}

/**
 * `GET /invitations?token=…`. The public preview behind an invitation link.
 *
 * Anonymous on purpose, and for two reasons rather than one. The endpoint is
 * `permitAll`, so a bearer token would be ignored; more importantly, a 401 here
 * is a *business answer* — the token is unknown, revoked, already spent or
 * past its date — and not an expired session. Sending it authenticated would
 * make the client try to renew a session in response, and sign an anonymous
 * visitor's empty session out in the process.
 *
 * The token is the only parameter the API takes in a query string anywhere, a
 * bounded exception the backend documents: the page has to be reachable from a
 * link in a message.
 */
export function previewInvitation(token: string): Promise<InvitationPreview> {
  return api.get<InvitationPreview>('/invitations', { params: { token }, anonymous: true })
}

/**
 * `POST /invitations/accept`. Redeems the invitation.
 *
 * `authenticated` is not a convenience. The endpoint is public, but it is not
 * anonymous in every case: when an account already exists for the invited
 * address the backend insists the caller *is* that account, reading the bearer
 * token to decide. So the request must carry one when the visitor is signed in,
 * and must not pretend to when they are not.
 *
 * The distinction matters because of what the client does with a 401. Every
 * authenticated call retries once behind a session renewal, and gives up on the
 * session when that fails. Here a 401 means "sign in as the invited account" or
 * "that link cannot be used" — neither of which a renewal fixes. Passing
 * `authenticated: false` for a visitor with no session keeps the client from
 * trying to renew one that was never there.
 */
export function acceptInvitation(
  body: AcceptInvitationBody,
  authenticated: boolean,
): Promise<AcceptedInvitation> {
  return api.post<AcceptedInvitation>('/invitations/accept', body, { anonymous: !authenticated })
}
