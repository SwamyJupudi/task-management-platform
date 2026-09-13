import { api } from '@/lib/api'

/**
 * The notification endpoints the application shell needs.
 *
 * Only the badge for now. The feed itself, the read/unread transitions and the
 * history are the notifications phase; this file grows into them rather than
 * being replaced, which is why it is here rather than inline in the component.
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
