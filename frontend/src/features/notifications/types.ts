/**
 * The wire shapes the notification endpoints return.
 *
 * Mirrors the backend's `NotificationResponse`.
 */

/** What a notification is about. Drives where following it goes. */
export type NotificationEntityType = 'PROJECT' | 'TASK' | 'COMMENT'

/**
 * The seven triggers the platform writes, as their machine codes.
 *
 * Listed so the feed can label a row by kind. Anything unrecognised still
 * renders — the message is composed server-side and does not depend on this.
 */
export type NotificationType =
  | 'task.assigned'
  | 'task.status_changed'
  | 'task.deadline_approaching'
  | 'comment.created'
  | 'comment.mentioned'
  | 'project.member_added'
  | 'project.status_changed'

/**
 * One thing somebody was told.
 *
 * `message` is composed when the row is read, never stored, so it is rendered
 * as it arrives rather than rebuilt here.
 *
 * `taskKey` carries more meaning than a label: it is the server's answer to
 * "is there still a task here you can open?", computed at render time rather
 * than trusted from the stored row. A project notification never has one,
 * because no task is involved. A task or comment notification loses one when
 * the task is no longer visible to the reader — which is rarer than it sounds,
 * because losing access by being removed from a project deletes the
 * notification outright rather than degrading it. What is left is the case the
 * cleanup cannot see, and it is why the key is checked rather than assumed.
 *
 * `actorUserId` may be null: the deadline scan is performed by the platform
 * rather than by a person.
 */
export interface Notification {
  id: string
  type: string
  /** Composed on read. Render it; do not compose one here. */
  message: string
  actorUserId: string | null
  actorName: string | null
  entityType: NotificationEntityType
  /** The task, comment or project this is about. */
  entityId: string
  projectId: string | null
  /** `PROJ-12`, or null when the target is gone or out of reach. */
  taskKey: string | null
  /**
   * The facts of the notification, differing by type.
   *
   * Comment notifications carry `taskId`, which is the only way to reach the
   * task a comment belongs to: `entityId` is the comment, and there is no
   * route to one.
   */
  metadata: Record<string, unknown>
  /** When it was read, or null while it is unread. */
  readAt: string | null
  createdAt: string
}
