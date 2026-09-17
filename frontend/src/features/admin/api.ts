import type { Team } from '@/features/teams'
import { api, type Page } from '@/lib/api'
import type { ActivityEntry } from '@/types/activity'

import type {
  ApproveAccountInput,
  Account,
  AccountFilters,
  PermissionEntry,
  PlatformAccount,
  PlatformProject,
  PlatformProjectFilters,
  Role,
  SystemStatistics,
  UpdateAccountInput,
  WorkspaceSummary,
} from './types'

/**
 * Every endpoint the admin panel calls, in one place.
 *
 * Thin on purpose: each function is a path, its parameters and a return type.
 *
 * **None of these takes a workspace as an authorization input.** The two that
 * accept one — the project overview's filter, and the role and team lookups —
 * use it as a filter over an already-authorized read or as the address of a
 * record, never as a scope that widens anything. That is the backend's rule for
 * this surface and it is worth restating on the client that consumes it, since
 * this is the only corner of the interface that reads across workspaces at all.
 *
 * The paths are split across four backend modules rather than gathered under
 * `/admin`, and that is deliberate on their side: the account verbs belong to
 * `users` and `auth`, and the role editor to `workspaces`, because each owns the
 * rule it enforces. Only the genuinely cross-workspace reads live under
 * `/admin`.
 */

// --- platform-wide reads (`/admin`) -----------------------------------------

/**
 * `GET /admin/statistics`.
 *
 * Needs `admin:read_system` on the platform. Not paged: a fixed set of panels,
 * bounded by the window rather than by a page size. A window under a day or
 * over the configured maximum is refused rather than clamped.
 */
export function systemStatistics(windowDays?: number | undefined): Promise<SystemStatistics> {
  return api.get<SystemStatistics>('/admin/statistics', { params: { windowDays } })
}

/**
 * `GET /admin/accounts`.
 *
 * Needs `user:read` on the platform. The ordinary directory at `GET /users` with
 * two columns added: how many workspaces each person reaches, and whether they
 * hold the platform role.
 */
export function accounts(
  filters: AccountFilters,
  page: number,
  size: number,
  sort: string,
): Promise<Page<PlatformAccount>> {
  return api.get<Page<PlatformAccount>>('/admin/accounts', {
    params: {
      search: filters.search,
      status: filters.status,
      // Only sent when it is on: the backend defaults it to false, and sending
      // `locked=false` would read as a filter for unlocked accounts.
      locked: filters.locked === true ? true : undefined,
      page,
      size,
      sort,
    },
  })
}

/**
 * `GET /admin/projects`.
 *
 * Needs `admin:read_system`, and deliberately not `project:read_any` — that
 * code is workspace-scoped and three seeded roles hold it, so it would gate
 * nothing here while appearing to.
 */
export function platformProjects(
  filters: PlatformProjectFilters,
  page: number,
  size: number,
  sort: string,
): Promise<Page<PlatformProject>> {
  return api.get<Page<PlatformProject>>('/admin/projects', {
    params: {
      workspaceId: filters.workspaceId,
      status: filters.status,
      ownerUserId: filters.ownerUserId,
      teamId: filters.teamId,
      page,
      size,
      sort,
    },
  })
}

/**
 * `GET /admin/activity`.
 *
 * Needs `admin:read_system` **and** `activity:read`, both on the platform. It
 * offers no sort: newest first is the only order a history is read in.
 *
 * Disjoint from the workspace history by construction — a row appears in
 * exactly one of the two, decided by whether it has a workspace — so this
 * cannot become a way into a workspace's own trail.
 */
export function platformActivity(page: number, size: number): Promise<Page<ActivityEntry>> {
  return api.get<Page<ActivityEntry>>('/admin/activity', { params: { page, size } })
}

// --- account administration (`/users`) --------------------------------------

/** `PATCH /users/{id}`. Name only; needs `user:update`. */
/**
 * `POST /workspaces/{id}/pending-users/{userId}/approve`. Needs `member:invite`
 * in that workspace, which a workspace ADMIN holds and a platform role satisfies
 * anywhere.
 *
 * The workspace is in the path rather than the body because it is the thing
 * being authorised: the caller's right to approve is their right to bring
 * somebody into that particular workspace.
 *
 * The whole of onboarding in one request: it grants the workspace role and,
 * when a project is named, the project membership, then activates the account —
 * all in one transaction on the server, so a half-admitted person is not a state
 * this screen can produce.
 */
export function approveAccount(
  workspaceId: string,
  userId: string,
  body: ApproveAccountInput,
): Promise<Account> {
  return api.post<Account>(`/workspaces/${workspaceId}/pending-users/${userId}/approve`, body)
}

export function updateAccount(userId: string, body: UpdateAccountInput): Promise<Account> {
  return api.patch<Account>(`/users/${userId}`, body)
}

/**
 * `POST /users/{id}/unlock`. Needs `user:update`.
 *
 * Idempotent: unlocking an account that is not locked succeeds and records
 * nothing, because nothing happened.
 */
export function unlockAccount(userId: string): Promise<Account> {
  return api.post<Account>(`/users/${userId}/unlock`)
}

/** `POST /users/{id}/deactivate`. Needs `user:deactivate`. Sessions end at the next request. */
export function deactivateAccount(userId: string): Promise<Account> {
  return api.post<Account>(`/users/${userId}/deactivate`)
}

/**
 * `POST /users/{id}/activate`. Needs `user:activate`.
 *
 * An account that never confirmed its address returns to awaiting verification
 * rather than to active, which is why the row is re-read rather than assumed.
 */
export function activateAccount(userId: string): Promise<Account> {
  return api.post<Account>(`/users/${userId}/activate`)
}

/**
 * `POST /users/{id}/password-reset`. Needs `user:update`.
 *
 * Starts a recovery; the single-use token is mailed to the account's own
 * address and the caller never sees it. There is no endpoint anywhere that sets
 * somebody else's password, which is why this screen offers none.
 */
export function startPasswordReset(userId: string): Promise<void> {
  return api.post<void>(`/users/${userId}/password-reset`)
}

/** `POST /users/{id}/resend-verification`. Needs `user:update`. Refused for a confirmed address. */
export function resendVerification(userId: string): Promise<void> {
  return api.post<void>(`/users/${userId}/resend-verification`)
}

/**
 * `PUT /users/{id}/platform-role`. Needs `platform_role:assign`.
 *
 * Takes no body: there is one platform role and a caller cannot name a role at
 * all, so it cannot name the wrong one.
 */
export function grantPlatformRole(userId: string): Promise<Account> {
  return api.put<Account>(`/users/${userId}/platform-role`)
}

/**
 * `DELETE /users/{id}/platform-role`. Needs `platform_role:assign`.
 *
 * Refused with 409 for the caller's own account and for the last remaining
 * administrator. Both refusals are the backend's, surfaced rather than
 * pre-empted — an interface that hid the control would have to know how many
 * administrators exist, and would be wrong the moment another one was made.
 */
export function revokePlatformRole(userId: string): Promise<void> {
  return api.delete<void>(`/users/${userId}/platform-role`)
}

// --- roles, permissions, workspaces and teams -------------------------------

/**
 * `GET /workspaces`.
 *
 * Needs `workspace:read` on the platform, and lists every workspace in the
 * installation. The role editor and the team overview are addressed by
 * workspace, so both start here.
 */
export function workspaces(page: number, size: number): Promise<Page<WorkspaceSummary>> {
  return api.get<Page<WorkspaceSummary>>('/workspaces', { params: { page, size } })
}

/**
 * `POST /workspaces`. Needs `workspace:create` on the platform.
 *
 * Seeds the three workspace roles and sets `EMPLOYEE` as the default in the same
 * transaction. A slug already in use answers 409; one that is not lowercase
 * words separated by single hyphens answers 400 naming the field.
 *
 * **It does not make the caller a member.** No membership row is written, so the
 * new workspace does not appear in `/auth/me` and `/w/{slug}` stays unreachable
 * for its own creator until somebody joins it. The screen says so and offers the
 * invitation that fixes it.
 */
export function createWorkspace(body: { name: string; slug: string }): Promise<WorkspaceSummary> {
  return api.post<WorkspaceSummary>('/workspaces', body)
}

/**
 * `DELETE /workspaces/{id}`. Needs `workspace:delete` on the platform.
 *
 * Soft, and the slug becomes available again. Deliberately not something a
 * workspace administrator can do to their own workspace: the code is granted to
 * no workspace role.
 */
export function deleteWorkspace(workspaceId: string): Promise<void> {
  return api.delete<void>(`/workspaces/${workspaceId}`)
}

/** `GET /permissions`. The global catalog, written by migrations. Needs `permission:read`. */
export function permissionCatalog(): Promise<PermissionEntry[]> {
  return api.get<PermissionEntry[]>('/permissions')
}

/**
 * `GET /workspaces/{id}/roles`. Needs `role:read` in that workspace.
 *
 * A platform grant satisfies that, because the backend resolves a workspace
 * check against the union of the caller's platform role and their membership.
 * That is what lets a platform administrator edit any workspace's roles without
 * joining it.
 */
export function workspaceRoles(workspaceId: string): Promise<Role[]> {
  return api.get<Role[]>(`/workspaces/${workspaceId}/roles`)
}

/**
 * `PUT /workspaces/{id}/roles/{slug}/permissions`. Needs `role:manage`.
 *
 * The complete set the role should hold afterwards, not a delta: a body of
 * additions and removals would need the client to know the current state to
 * compute it, and two administrators editing at once would merge into a set
 * neither chose. An empty list is legal and means the role grants nothing.
 *
 * Answers 409 when the workspace is archived, and when the change would leave
 * the caller unable to edit roles at all.
 */
export function replaceRolePermissions(
  workspaceId: string,
  roleSlug: string,
  permissions: string[],
): Promise<Role> {
  return api.put<Role>(`/workspaces/${workspaceId}/roles/${roleSlug}/permissions`, { permissions })
}

/** `GET /workspaces/{id}/teams`. Needs `team:read`, which a platform role supplies everywhere. */
export function workspaceTeams(
  workspaceId: string,
  status: string | undefined,
  page: number,
  size: number,
): Promise<Page<Team>> {
  return api.get<Page<Team>>(`/workspaces/${workspaceId}/teams`, {
    params: { status, page, size },
  })
}
