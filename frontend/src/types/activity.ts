/**
 * One recorded action, as the audit trail reads it back.
 *
 * Mirrors the backend's `ActivityResponse`. Kept in `types/` rather than in a
 * feature because the same record is read in more than one place: a task's
 * history, a project's, and the dashboard's "what you did" panel all show it.
 *
 * `summary` is composed by the backend when the row is read, from the
 * identifiers in it and the names those resolve to now. Nothing of the sort is
 * stored, so a sentence written here would go stale the first time somebody was
 * renamed. Render it rather than building one.
 *
 * The actor may be null: an action taken by the platform rather than by a
 * person has none, and so does one whose account has since been removed.
 */
export interface ActivityEntry {
  id: string
  workspaceId: string
  actorUserId: string | null
  actorEmail: string | null
  actorName: string | null
  /** Machine-readable, e.g. `task.status_changed`. */
  action: string
  entityType: string
  entityId: string
  projectId: string | null
  /** The facts of the action, differing by action. */
  metadata: Record<string, unknown>
  /** Composed on read, never stored. */
  summary: string
  /** Correlates the record with the request that caused it. */
  requestId: string | null
  createdAt: string
}
