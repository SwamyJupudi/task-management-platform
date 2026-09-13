import { paths } from '@/app/routes/paths'

import type { Notification } from './types'

/**
 * Where following a notification goes, or nothing.
 *
 * Three shapes, one per entity type, and each uses only what the response
 * already carries:
 *
 *  - A task notification names the task in `entityId`.
 *  - A comment notification names the *comment* in `entityId`, and there is no
 *    route to a comment. The task it belongs to arrives in `metadata.taskId`,
 *    which is the only way to reach it, and the destination opens the task's
 *    discussion rather than dropping the reader on its details.
 *  - A project notification names the project in `entityId`.
 *
 * Returning null is a real answer rather than a failure. The backend recomputes
 * `taskKey` on every read rather than trusting the stored row, so a task or
 * comment row without one is the server saying there is nothing left to open,
 * and it is rendered as text rather than as a link that would land on a 404.
 *
 * That guard fires less often than it looks, and the reason is worth knowing
 * before anybody decides it is dead code: the usual ways of losing access —
 * being removed from a project, leaving the workspace, the task being deleted —
 * delete the notification rather than degrading it. The check covers what the
 * cleanup cannot reach, which is a task that moves out of view with nobody's
 * membership changing.
 *
 * The check is deliberately not applied to project rows, which never carry a
 * key at all. Gating on it there would make every project notification
 * unfollowable. One pointing at a project the reader has since lost is left to
 * the project screen's own not-found state.
 */
export function destinationOf(notification: Notification, workspaceSlug: string): string | null {
  if (workspaceSlug === '') return null

  switch (notification.entityType) {
    case 'TASK': {
      // No key means no longer reachable, whatever the row still says.
      if (notification.taskKey === null) return null
      return paths.workspace.task(workspaceSlug, notification.entityId)
    }

    case 'COMMENT': {
      if (notification.taskKey === null) return null
      const taskId = notification.metadata['taskId']
      if (typeof taskId !== 'string' || taskId === '') return null
      // Straight to the discussion, which is the panel the comment is in.
      return `${paths.workspace.task(workspaceSlug, taskId)}?panel=comments`
    }

    case 'PROJECT':
      return paths.workspace.project(workspaceSlug, notification.entityId)

    default:
      return null
  }
}

/**
 * A short label for the kind of notification, for the row's leading chip.
 *
 * The sentence itself is composed server-side and rendered untouched; this is
 * only a category, and an unknown code falls back to the code rather than
 * being hidden, so a type added later still reads as something.
 */
export function labelOfType(type: string): string {
  switch (type) {
    case 'task.assigned':
      return 'Assigned'
    case 'task.status_changed':
      return 'Status'
    case 'task.deadline_approaching':
      return 'Due soon'
    case 'comment.created':
      return 'Comment'
    case 'comment.mentioned':
      return 'Mention'
    case 'project.member_added':
      return 'Project'
    case 'project.status_changed':
      return 'Project'
    default:
      return type
  }
}
