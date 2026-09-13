import { api, type Page } from '@/lib/api'

import type { Invitation, InviteInput, WorkspaceMember, WorkspaceRole } from './types'

/**
 * The workspace's roster, its roles and its invitations.
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

/** `GET /workspaces/{id}/invitations`. Needs `member:read`. */
export function listInvitations(
  workspaceId: string,
  page: number,
  size: number,
): Promise<Page<Invitation>> {
  return api.get<Page<Invitation>>(`${base(workspaceId)}/invitations`, {
    params: { page, size },
  })
}

/**
 * `POST /workspaces/{id}/invitations`. Needs `member:invite`.
 *
 * Any outstanding invitation to the same address is superseded, which is the
 * only "send it again" the platform has — there is no resend endpoint.
 */
export function invite(workspaceId: string, body: InviteInput): Promise<Invitation> {
  return api.post<Invitation>(`${base(workspaceId)}/invitations`, body)
}

/** `DELETE /workspaces/{id}/invitations/{id}`. Needs `member:invite`. */
export function revokeInvitation(workspaceId: string, invitationId: string): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/invitations/${invitationId}`)
}
