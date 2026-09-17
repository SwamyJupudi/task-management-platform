/**
 * The wire shapes the workspace membership endpoints exchange.
 *
 * Each mirrors a record in the Spring `workspaces.dto` package.
 */

/** One person's membership of a workspace, with enough profile to render a row. */
export interface WorkspaceMember {
  userId: string
  email: string
  firstName: string
  lastName: string
  roleSlug: string
  roleName: string
  /** Whether the account may currently sign in, e.g. `ACTIVE`. */
  userStatus: string
  joinedAt: string
}

/** A role of this workspace, and what it grants. */
export interface WorkspaceRole {
  id: string
  slug: string
  name: string
  scope: string
  /** Seeded roles cannot be deleted. */
  system: boolean
  permissions: string[]
}


/**
 * A registration waiting to be admitted.
 *
 * The platform's `UserResponse`, not a workspace member: somebody in this list
 * belongs to no workspace yet, which is exactly what approving them changes.
 */
export interface PendingUser {
  id: string
  email: string
  firstName: string
  lastName: string
  status: string
  createdAt: string
}
