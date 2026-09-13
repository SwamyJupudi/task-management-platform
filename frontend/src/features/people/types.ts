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

/**
 * One outstanding or settled invitation.
 *
 * There is no resend endpoint. Inviting the same address again supersedes
 * whatever is outstanding, which is what "send another" means here.
 */
export interface Invitation {
  id: string
  workspaceId: string
  email: string
  roleSlug: string
  /** `PENDING`, and the settled states the backend records. */
  status: string
  expiresAt: string
  createdAt: string
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

/** The body of `POST /workspaces/{id}/invitations`. */
export interface InviteInput {
  email: string
  /** Omitted means the workspace's default role. */
  roleSlug?: string | undefined
}
