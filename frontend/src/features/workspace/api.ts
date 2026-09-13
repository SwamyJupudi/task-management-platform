import { api } from '@/lib/api'

import type { UpdateWorkspaceInput, Workspace } from './types'

/**
 * The endpoints one workspace's settings screen uses.
 *
 * Every one is addressed by workspace and guarded rather than annotated, which
 * is the backend's way of answering 404 for a workspace the caller has nothing
 * to do with rather than 403. The interface never has to tell those two apart:
 * both mean the screen cannot be shown.
 *
 * Creating and listing workspaces are deliberately absent. Both are gated on a
 * platform permission that no workspace role holds, so they belong to the admin
 * panel rather than here.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}`

/** `GET /workspaces/{id}`. Needs `workspace:read`, which every seeded role has. */
export function getWorkspace(workspaceId: string): Promise<Workspace> {
  return api.get<Workspace>(base(workspaceId))
}

/**
 * `PATCH /workspaces/{id}`. Needs `workspace:update`, which only ADMIN holds.
 *
 * Answers 409 when the workspace is archived: the guard behind it is the
 * change-checking one, and an archived workspace is frozen against every edit.
 */
export function updateWorkspace(
  workspaceId: string,
  body: UpdateWorkspaceInput,
): Promise<Workspace> {
  return api.patch<Workspace>(base(workspaceId), body)
}

/**
 * `POST /workspaces/{id}/archive`. Needs `workspace:archive`.
 *
 * Its own permission rather than `workspace:update`, because it is a decision
 * about the whole workspace rather than an edit to one of its fields. Archiving
 * one that is already archived answers 409.
 */
export function archiveWorkspace(workspaceId: string): Promise<Workspace> {
  return api.post<Workspace>(`${base(workspaceId)}/archive`)
}

/** `POST /workspaces/{id}/unarchive`. Needs `workspace:archive`. 409 when not archived. */
export function unarchiveWorkspace(workspaceId: string): Promise<Workspace> {
  return api.post<Workspace>(`${base(workspaceId)}/unarchive`)
}
