/**
 * Session shapes, mirroring the backend's `CurrentUser`, `Membership` and
 * `WorkspacePermissions` records.
 *
 * Kept in `types/` rather than in the auth feature because the layout, the
 * route guards and the workspace switcher all read them, and none of those
 * should have to import from a feature folder.
 */

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  status: string
}

/** A workspace the caller belongs to, and the role they hold there. */
export interface Membership {
  workspaceId: string
  workspaceName: string
  workspaceSlug: string
  roleSlug: string
  roleName: string
}

/** The body of `GET /auth/me`. */
export interface CurrentUser {
  user: User
  /** Platform role slug, e.g. `SUPER_ADMIN`. Absent for ordinary accounts. */
  platformRole: string | null
  /** Permission codes held platform-wide. */
  platformPermissions: string[]
  memberships: Membership[]
}

/** The body of `GET /workspaces/{workspaceId}/me`. */
export interface WorkspacePermissions {
  permissions: string[]
}
