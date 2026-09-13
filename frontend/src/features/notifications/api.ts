import { api, type Page } from '@/lib/api'

import type { Notification } from './types'

/**
 * Everything one person was told, in one workspace.
 *
 * No permission code gates any of this, and that is deliberate on the backend's
 * part: a notification has exactly one audience, so a `notification:read` grant
 * would be held by everybody and would gate nothing. What is enforced instead
 * is membership — a workspace the caller has nothing to do with answers 404 —
 * and the recipient, which every method narrows to. There is no path,
 * parameter or role that reads somebody else's feed.
 */

export interface UnreadCount {
  unread: number
}

/**
 * `GET /workspaces/{workspaceId}/notifications/unread-count`.
 *
 * No permission code gates it. A notification has exactly one audience, so the
 * backend names the recipient instead and answers 404 for a workspace the
 * caller has nothing to do with.
 */
export function unreadCount(workspaceId: string): Promise<UnreadCount> {
  return api.get<UnreadCount>(`/workspaces/${workspaceId}/notifications/unread-count`)
}

/**
 * `GET /workspaces/{workspaceId}/notifications`.
 *
 * Newest first. `unread` narrows to what is still waiting; the backend defaults
 * it to false, so it is sent only when true.
 */
export function listNotifications(
  workspaceId: string,
  unread: boolean,
  page: number,
  size: number,
): Promise<Page<Notification>> {
  return api.get<Page<Notification>>(`/workspaces/${workspaceId}/notifications`, {
    params: { unread: unread ? true : undefined, page, size },
  })
}

/**
 * `PATCH /workspaces/{workspaceId}/notifications/{id}/read`.
 *
 * A PATCH because it changes one field of one resource, and idempotent: a
 * second call succeeds and leaves the original moment alone. Somebody else's
 * answers 404.
 */
export function markRead(workspaceId: string, notificationId: string): Promise<void> {
  return api.patch<void>(`/workspaces/${workspaceId}/notifications/${notificationId}/read`)
}

/**
 * `POST /workspaces/{workspaceId}/notifications/read-all`.
 *
 * A POST because it names no resource and acts across a collection. It reports
 * how many rows it moved, which is what lets the caller say whether anything
 * actually happened.
 */
export function markAllRead(workspaceId: string): Promise<{ marked: number }> {
  return api.post<{ marked: number }>(`/workspaces/${workspaceId}/notifications/read-all`)
}
