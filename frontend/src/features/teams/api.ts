import { api, type Page } from '@/lib/api'

import type { CreateTeamInput, Team, TeamMember, TeamStatus, UpdateTeamInput } from './types'

/**
 * Every team endpoint, in one place.
 *
 * The workspace comes from the caller rather than from ambient state, matching
 * the backend, which takes it from the path and never from a header.
 *
 * The listing takes a status and nothing else. There is no search and no other
 * filter, so the screen offers neither.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}/teams`

/** `GET /teams`. Optionally filtered by status; a bad one is refused, not ignored. */
export function listTeams(
  workspaceId: string,
  status: TeamStatus | undefined,
  page: number,
  size: number,
): Promise<Page<Team>> {
  return api.get<Page<Team>>(base(workspaceId), { params: { status, page, size } })
}

/** `GET /teams/{id}`. Answers 404 for a team the caller may not see. */
export function getTeam(workspaceId: string, teamId: string): Promise<Team> {
  return api.get<Team>(`${base(workspaceId)}/${teamId}`)
}

/** `POST /teams`. A named lead is added to the team as well. */
export function createTeam(workspaceId: string, body: CreateTeamInput): Promise<Team> {
  return api.post<Team>(base(workspaceId), body)
}

/** `PATCH /teams/{id}`. Omitted fields are left alone. */
export function updateTeam(
  workspaceId: string,
  teamId: string,
  body: UpdateTeamInput,
): Promise<Team> {
  return api.patch<Team>(`${base(workspaceId)}/${teamId}`, body)
}

/** `POST /teams/{id}/archive`. Reversible; the roster and the lead are kept. */
export function archiveTeam(workspaceId: string, teamId: string): Promise<Team> {
  return api.post<Team>(`${base(workspaceId)}/${teamId}/archive`)
}

/** `POST /teams/{id}/unarchive`. */
export function unarchiveTeam(workspaceId: string, teamId: string): Promise<Team> {
  return api.post<Team>(`${base(workspaceId)}/${teamId}/unarchive`)
}

/**
 * `DELETE /teams/{id}`. Soft delete; the name becomes available again.
 *
 * Needs `team:delete`, which a team lead does not hold — the one team operation
 * a lead cannot perform on their own team.
 */
export function deleteTeam(workspaceId: string, teamId: string): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/${teamId}`)
}

/** `GET /teams/{id}/members`. */
export function listTeamMembers(
  workspaceId: string,
  teamId: string,
  page = 0,
  size = 50,
): Promise<Page<TeamMember>> {
  return api.get<Page<TeamMember>>(`${base(workspaceId)}/${teamId}/members`, {
    params: { page, size },
  })
}

/** `POST /teams/{id}/members`. The person must already be in the workspace. */
export function addTeamMember(
  workspaceId: string,
  teamId: string,
  userId: string,
): Promise<TeamMember> {
  return api.post<TeamMember>(`${base(workspaceId)}/${teamId}/members`, { userId })
}

/** `DELETE /teams/{id}/members/{userId}`. Refused if they lead the team. */
export function removeTeamMember(
  workspaceId: string,
  teamId: string,
  userId: string,
): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/${teamId}/members/${userId}`)
}

/** `PUT /teams/{id}/lead`. Adds them to the team if they are not in it. */
export function assignLead(workspaceId: string, teamId: string, userId: string): Promise<Team> {
  return api.put<Team>(`${base(workspaceId)}/${teamId}/lead`, { userId })
}

/** `DELETE /teams/{id}/lead`. They stay a member of the team. */
export function clearLead(workspaceId: string, teamId: string): Promise<Team> {
  return api.delete<Team>(`${base(workspaceId)}/${teamId}/lead`)
}
