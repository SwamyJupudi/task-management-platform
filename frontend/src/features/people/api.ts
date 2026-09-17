import { api, type Page } from '@/lib/api'

import type { PendingUser, WorkspaceMember, WorkspaceRole } from './types'

/**
 * The workspace's roster and its roles.
 *
 * This feature owns the member directory, which is why the lookups other
 * features need — who is in this workspace, and which roles exist — are
 * exported from here through `index.ts` rather than copied again. Projects,
 * tasks and collaboration each grew their own copy before this existed and can
 * adopt these when they are next touched.
 *
 * Neither the member listing nor the invitation listing takes a search or a
 * filter. Both are paged and nothing more, so neither screen offers a search
 * box it could not honour.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}`

/** `GET /workspaces/{id}/members`. Needs `member:read`. */
/**
 * `GET /workspaces/{id}/pending-users`. Needs `member:invite` in this workspace.
 *
 * Not scoped to the workspace, and it cannot be: a registration belongs to no
 * workspace until somebody approves it into one. What the workspace scopes is
 * the right to see the queue at all.
 */
export function listPendingUsers(
  workspaceId: string,
  page: number,
  size: number,
): Promise<Page<PendingUser>> {
  return api.get<Page<PendingUser>>(`${base(workspaceId)}/pending-users`, {
    params: { page, size },
  })
}

/** `POST /workspaces/{id}/pending-users/{userId}/approve`. Needs `member:invite`. */
export function approvePendingUser(
  workspaceId: string,
  userId: string,
  body: { roleSlug: string; projectId?: string | undefined },
): Promise<PendingUser> {
  return api.post<PendingUser>(`${base(workspaceId)}/pending-users/${userId}/approve`, body)
}

export function listMembers(
  workspaceId: string,
  page: number,
  size: number,
): Promise<Page<WorkspaceMember>> {
  return api.get<Page<WorkspaceMember>>(`${base(workspaceId)}/members`, {
    params: { page, size },
  })
}

/** `PATCH /workspaces/{id}/members/{userId}`. Needs `member:assign_role`. */
export function changeMemberRole(
  workspaceId: string,
  userId: string,
  roleSlug: string,
): Promise<WorkspaceMember> {
  return api.patch<WorkspaceMember>(`${base(workspaceId)}/members/${userId}`, { roleSlug })
}

/** `DELETE /workspaces/{id}/members/{userId}`. Needs `member:remove`. */
export function removeMember(workspaceId: string, userId: string): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/members/${userId}`)
}

/**
 * `GET /workspaces/{id}/roles`. Needs `role:read`.
 *
 * Not paged: a workspace has three seeded roles and no way yet to create more.
 */
export function listRoles(workspaceId: string): Promise<WorkspaceRole[]> {
  return api.get<WorkspaceRole[]>(`${base(workspaceId)}/roles`)
}

