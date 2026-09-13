import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'

import type { Team } from '@/features/teams'
import { usePermissions } from '@/hooks/use-permissions'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'
import { useSessionStore } from '@/stores/session-store'
import type { ActivityEntry } from '@/types/activity'

import * as adminApi from './api'
import { ACTIVITY_PAGE_SIZE, PAGE_SIZE, WORKSPACE_OPTION_SIZE } from './constants'
import type {
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
 * The admin panel's server state.
 *
 * **No key carries a workspace**, and that absence is the point. Every other
 * feature keys by workspace because two workspaces are two different answers;
 * these are installation-wide reads with exactly one answer, so a workspace in
 * the key would be meaningless. The two screens addressed by workspace — the
 * role editor and the team overview — key by the workspace they are *looking
 * at*, which is a filter rather than a tenant.
 *
 * Every `enabled` repeats the platform permission its endpoint is gated on,
 * asked through `hasOnPlatform` rather than the ordinary union. A workspace
 * administrator holds several of these codes inside their own workspace and
 * none of them here, so the union would issue requests that are certain to be
 * refused.
 */

const STALE_MS = 60_000

const adminRoot = [...queryKeys.admin] as const

// --- permissions ------------------------------------------------------------

/**
 * What the signed-in account may do in the admin panel, in the backend's codes.
 *
 * All platform-scoped, all mirroring a `@perm.onPlatform` on the endpoint
 * behind them. Nothing here reads a role name: `SUPER_ADMIN` happens to hold
 * every one of these, but it holds them as rows in the catalog like any other
 * role, and asking for the role instead of the grant would break the moment a
 * second platform role existed.
 */
export interface AdminPermissions {
  /** Statistics, the project overview, and half of the audit trail's gate. */
  canReadSystem: boolean
  canReadAccounts: boolean
  /** Profile edits, unlock, password recovery and resending verification share this code. */
  canUpdateAccounts: boolean
  canActivateAccounts: boolean
  canDeactivateAccounts: boolean
  canAssignPlatformRole: boolean
  /** Both codes, because the platform trail insists on both. */
  canReadPlatformActivity: boolean
  canListWorkspaces: boolean
  canCreateWorkspaces: boolean
  canDeleteWorkspaces: boolean
  canReadRoles: boolean
  canManageRoles: boolean
  canReadPermissionCatalog: boolean
  canReadTeams: boolean
}

export function useAdminPermissions(): AdminPermissions {
  const { hasOnPlatform, hasAllOnPlatform } = usePermissions()

  return {
    canReadSystem: hasOnPlatform('admin:read_system'),
    canReadAccounts: hasOnPlatform('user:read'),
    canUpdateAccounts: hasOnPlatform('user:update'),
    canActivateAccounts: hasOnPlatform('user:activate'),
    canDeactivateAccounts: hasOnPlatform('user:deactivate'),
    canAssignPlatformRole: hasOnPlatform('platform_role:assign'),
    canReadPlatformActivity: hasAllOnPlatform(['admin:read_system', 'activity:read']),
    canListWorkspaces: hasOnPlatform('workspace:read'),
    canCreateWorkspaces: hasOnPlatform('workspace:create'),
    canDeleteWorkspaces: hasOnPlatform('workspace:delete'),
    canReadRoles: hasOnPlatform('role:read'),
    canManageRoles: hasOnPlatform('role:manage'),
    canReadPermissionCatalog: hasOnPlatform('permission:read'),
    canReadTeams: hasOnPlatform('team:read'),
  }
}

/** The signed-in account's own id, so the panel can mark and protect that row. */
export function useCurrentUserId(): string | null {
  return useSessionStore((state) => state.user?.id ?? null)
}

// --- platform-wide reads ----------------------------------------------------

/** Every figure the panel shows, over a trailing window. */
export function useSystemStatistics(windowDays: number): UseQueryResult<SystemStatistics> {
  const { canReadSystem } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'statistics', windowDays],
    queryFn: () => adminApi.systemStatistics(windowDays),
    enabled: canReadSystem,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/** The administrative account directory, paged. */
export function useAccounts(
  filters: AccountFilters,
  page: number,
  sort: string,
): UseQueryResult<Page<PlatformAccount>> {
  const { canReadAccounts } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'accounts', filters, page, sort],
    queryFn: () => adminApi.accounts(filters, page, PAGE_SIZE, sort),
    enabled: canReadAccounts,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/** Every project in the installation, paged. */
export function usePlatformProjects(
  filters: PlatformProjectFilters,
  page: number,
  sort: string,
): UseQueryResult<Page<PlatformProject>> {
  const { canReadSystem } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'projects', filters, page, sort],
    queryFn: () => adminApi.platformProjects(filters, page, PAGE_SIZE, sort),
    enabled: canReadSystem,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/** The platform audit trail, newest first. No sort: a history has one order. */
export function usePlatformActivity(page: number): UseQueryResult<Page<ActivityEntry>> {
  const { canReadPlatformActivity } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'activity', page],
    queryFn: () => adminApi.platformActivity(page, ACTIVITY_PAGE_SIZE),
    enabled: canReadPlatformActivity,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

// --- lookups ----------------------------------------------------------------

/**
 * Every workspace in the installation, for the pickers and the filters.
 *
 * One page of a hundred rather than every page, matching the other option
 * lookups in the application. An installation with more workspaces than that
 * needs a searchable picker, which no endpoint supports — the listing takes no
 * search parameter — so the screen says what it is showing rather than
 * pretending the list is complete.
 */
export function useWorkspaceOptions(): UseQueryResult<WorkspaceSummary[]> {
  const { canListWorkspaces } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'workspaces', 'options'],
    queryFn: async () => {
      const page = await adminApi.workspaces(0, WORKSPACE_OPTION_SIZE)
      return page.content
    },
    enabled: canListWorkspaces,
    staleTime: 5 * 60_000,
  })
}

/**
 * Every workspace in the installation, paged.
 *
 * Separate from {@link useWorkspaceOptions}, which fetches one page of a hundred
 * to fill a dropdown. Sharing a key between them would make the pickers on the
 * roles and teams screens jump to whatever page this listing was last on.
 */
export function useWorkspaces(page: number): UseQueryResult<Page<WorkspaceSummary>> {
  const { canListWorkspaces } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'workspaces', 'list', page],
    queryFn: () => adminApi.workspaces(page, PAGE_SIZE),
    enabled: canListWorkspaces,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/**
 * Creating and removing workspaces, and the invitation that follows creation.
 *
 * Creating one does not join it, so the two are offered together rather than
 * the screen pretending otherwise. The invitation is an ordinary one: it goes
 * to the address, and accepting it is what writes the membership row.
 *
 * The whole admin root is invalidated on a write. A new or removed workspace
 * moves the statistics, the pickers on two other screens and this listing, and
 * one broad invalidation cannot be wrong about which.
 */
export function useWorkspaceMutations() {
  const queryClient = useQueryClient()

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
  }

  const create = useMutation({
    mutationFn: (body: { name: string; slug: string }) => adminApi.createWorkspace(body),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (workspaceId: string) => adminApi.deleteWorkspace(workspaceId),
    onSuccess: invalidate,
  })

  // No invalidation: an invitation changes nothing this panel displays. It is
  // the people screen inside that workspace that lists them.
  const inviteSelf = useMutation({
    mutationFn: ({ workspaceId, email }: { workspaceId: string; email: string }) =>
      adminApi.inviteToWorkspace(workspaceId, email),
  })

  return { create, remove, inviteSelf }
}

/**
 * The permission catalog.
 *
 * Cached for an hour: the rows are written by migrations, so they cannot change
 * while the application is running.
 */
export function usePermissionCatalog(): UseQueryResult<PermissionEntry[]> {
  const { canReadPermissionCatalog } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'permissions'],
    queryFn: () => adminApi.permissionCatalog(),
    enabled: canReadPermissionCatalog,
    staleTime: 60 * 60_000,
  })
}

/** The roles of one workspace and what each grants. */
export function useWorkspaceRoles(workspaceId: string | undefined): UseQueryResult<Role[]> {
  const { canReadRoles } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'roles', workspaceId],
    queryFn: () => adminApi.workspaceRoles(workspaceId as string),
    enabled: canReadRoles && workspaceId !== undefined && workspaceId !== '',
    staleTime: STALE_MS,
  })
}

/** The teams of one workspace, paged. */
export function useWorkspaceTeams(
  workspaceId: string | undefined,
  status: string | undefined,
  page: number,
): UseQueryResult<Page<Team>> {
  const { canReadTeams } = useAdminPermissions()

  return useQuery({
    queryKey: [...adminRoot, 'teams', workspaceId, status ?? 'any', page],
    queryFn: () => adminApi.workspaceTeams(workspaceId as string, status, page, PAGE_SIZE),
    enabled: canReadTeams && workspaceId !== undefined && workspaceId !== '',
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

// --- writes -----------------------------------------------------------------

/**
 * Every account verb, sharing one invalidation.
 *
 * Broad on purpose. Each of these changes a row in the directory, and most also
 * move a figure on the statistics screen — deactivating somebody changes the
 * account breakdown, granting the platform role changes nothing there but is
 * recorded in the audit trail. Invalidating the whole admin root is one line
 * that cannot be wrong, and the panel is not a screen anybody refreshes in a
 * tight loop.
 *
 * None of these is optimistic. Every one of them is a decision about somebody's
 * access, the server may refuse it — a 409 for revoking the last administrator,
 * a 400 for resending to a confirmed address — and showing the change before it
 * is real would be showing a state that may not exist.
 */
export function useAccountMutations() {
  const queryClient = useQueryClient()

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
  }

  const updateProfile = useMutation({
    mutationFn: ({ userId, body }: { userId: string; body: UpdateAccountInput }) =>
      adminApi.updateAccount(userId, body),
    onSuccess: invalidate,
  })

  const activate = useMutation({
    mutationFn: (userId: string) => adminApi.activateAccount(userId),
    onSuccess: invalidate,
  })

  const deactivate = useMutation({
    mutationFn: (userId: string) => adminApi.deactivateAccount(userId),
    onSuccess: invalidate,
  })

  const unlock = useMutation({
    mutationFn: (userId: string) => adminApi.unlockAccount(userId),
    onSuccess: invalidate,
  })

  // Neither of these changes a row, so neither invalidates anything. They send
  // a message and return nothing; a refetch afterwards would be a request that
  // could not come back different.
  const startPasswordReset = useMutation({
    mutationFn: (userId: string) => adminApi.startPasswordReset(userId),
  })

  const resendVerification = useMutation({
    mutationFn: (userId: string) => adminApi.resendVerification(userId),
  })

  const grantPlatformRole = useMutation({
    mutationFn: (userId: string) => adminApi.grantPlatformRole(userId),
    onSuccess: invalidate,
  })

  const revokePlatformRole = useMutation({
    mutationFn: (userId: string) => adminApi.revokePlatformRole(userId),
    onSuccess: invalidate,
  })

  return {
    updateProfile,
    activate,
    deactivate,
    unlock,
    startPasswordReset,
    resendVerification,
    grantPlatformRole,
    revokePlatformRole,
  }
}

/**
 * Replacing what a role grants.
 *
 * Invalidates the whole admin root rather than just the edited role: changing
 * what a role may do changes what its holders may do, and the caller may have
 * just changed their own.
 */
export function useRoleMutations() {
  const queryClient = useQueryClient()

  const replacePermissions = useMutation({
    mutationFn: ({
      workspaceId,
      roleSlug,
      permissions,
    }: {
      workspaceId: string
      roleSlug: string
      permissions: string[]
    }) => adminApi.replaceRolePermissions(workspaceId, roleSlug, permissions),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
      // A grant the signed-in account itself holds may have moved, and the
      // session's permission set is what every other screen renders from.
      void queryClient.invalidateQueries({ queryKey: queryKeys.auth })
    },
  })

  return { replacePermissions }
}
