/**
 * The wire shapes the workspace endpoints exchange.
 *
 * Mirrors `WorkspaceResponse` and `UpdateWorkspaceRequest` in the Spring
 * `workspaces.dto` package.
 */

/** The two lifecycle states. Archiving is reversible and freezes every change inside. */
export type WorkspaceStatus = 'ACTIVE' | 'ARCHIVED'

/**
 * A workspace and its settings.
 *
 * `slug` is present and is never editable: it appears in links that have
 * already been shared, so the backend fixes it at creation and offers no field
 * for changing it.
 *
 * `defaultRoleSlug` names a role of this workspace — the one somebody invited
 * without a named role arrives as.
 */
export interface Workspace {
  id: string
  name: string
  slug: string
  description: string | null
  /** IANA zone name. Every date in this workspace is read in it. */
  timezone: string
  defaultRoleSlug: string
  status: WorkspaceStatus
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * The body of `PATCH /workspaces/{id}`.
 *
 * Every field is optional and an omitted one is left alone, which is not a
 * convenience but the contract: the service checks each against null before
 * touching it. Sending the whole form on every save would overwrite fields
 * nobody edited, so the screen builds this from the dirty ones only.
 *
 * A blank description clears it. That is the single way to remove a value
 * through this request, and it works because blank and absent are different
 * things here.
 */
export interface UpdateWorkspaceInput {
  name?: string | undefined
  description?: string | undefined
  timezone?: string | undefined
  defaultRoleSlug?: string | undefined
}
