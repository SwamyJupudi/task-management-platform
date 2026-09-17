import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'

import * as peopleApi from './api'
import type { PendingUser, WorkspaceMember, WorkspaceRole } from './types'

/**
 * The people feature's server state.
 *
 * Every key carries the workspace id, for the reason every other feature's
 * does: two workspaces are two different rosters, and a key that left it out
 * would serve one workspace's people under the other's name.
 */

const STALE_MS = 60_000
const PAGE_SIZE = 20

/** How many the pickers ask for. A workspace roster is not a long list. */
const OPTION_PAGE_SIZE = 100

function peopleRoot(workspaceId: string) {
  return [...queryKeys.users, workspaceId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may do with the roster.
 *
 * Codes, never role names. Inviting and withdrawing an invitation share
 * `member:invite`, which is the backend's arrangement rather than a shortcut:
 * withdrawing is undoing something the same grant allowed.
 */
export interface PeoplePermissions {
  canReadMembers: boolean
  canInvite: boolean
  canAssignRole: boolean
  canRemove: boolean
  canReadRoles: boolean
}

export function usePeoplePermissions(): PeoplePermissions {
  const { has } = usePermissions()

  return {
    canReadMembers: has('member:read'),
    canInvite: has('member:invite'),
    canAssignRole: has('member:assign_role'),
    canRemove: has('member:remove'),
    canReadRoles: has('role:read'),
  }
}

// --- queries ----------------------------------------------------------------

/** The roster, paged. The endpoint offers no search or filter, so neither does this. */
export function useMembers(page: number): UseQueryResult<Page<WorkspaceMember>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadMembers } = usePeoplePermissions()

  return useQuery({
    queryKey: [...peopleRoot(workspaceId ?? 'none'), 'members', page],
    queryFn: () => peopleApi.listMembers(workspaceId as string, page, PAGE_SIZE),
    enabled: workspaceId !== null && canReadMembers,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/**
 * The whole roster in one page, for the pickers.
 *
 * Separate from `useMembers` on purpose: that one is the directory the user
 * pages through, this one fills a dropdown, and sharing a key between them
 * would make the directory jump to a hundred rows the first time a picker
 * opened.
 *
 * Exported through the feature's index so teams and anything else can reach it
 * without a fourth copy of the same call.
 */
export function useWorkspaceMembers(): UseQueryResult<WorkspaceMember[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadMembers } = usePeoplePermissions()

  return useQuery({
    queryKey: [...peopleRoot(workspaceId ?? 'none'), 'options'],
    queryFn: async () => {
      const page = await peopleApi.listMembers(workspaceId as string, 0, OPTION_PAGE_SIZE)
      return page.content
    },
    enabled: workspaceId !== null && canReadMembers,
    staleTime: 5 * 60_000,
  })
}

/** The workspace's roles, for the invite dialog and the role picker. */
export function useWorkspaceRoles(): UseQueryResult<WorkspaceRole[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadRoles } = usePeoplePermissions()

  return useQuery({
    queryKey: [...peopleRoot(workspaceId ?? 'none'), 'roles'],
    queryFn: () => peopleApi.listRoles(workspaceId as string),
    enabled: workspaceId !== null && canReadRoles,
    staleTime: 5 * 60_000,
  })
}

// --- mutations --------------------------------------------------------------

/**
 * Every roster write, sharing one invitation.
 *
 * Broad on purpose: accepting somebody into the workspace changes the roster
 * and settles their invitation, and a role change moves a row in both the
 * directory and every picker built from it.
 */
/**
 * Registrations waiting to be admitted to this workspace.
 *
 * Only fetched for somebody who could act on it: `member:invite` is what the
 * endpoint requires, a workspace ADMIN holds it, and TEAM_LEAD and EMPLOYEE do
 * not. Asking anyway would produce a 403 the screen can do nothing with.
 */
export function usePendingUsers(page: number): UseQueryResult<Page<PendingUser>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canInvite } = usePeoplePermissions()

  return useQuery({
    queryKey: [...peopleRoot(workspaceId ?? 'none'), 'pending-users', page],
    queryFn: () => peopleApi.listPendingUsers(workspaceId as string, page, PAGE_SIZE),
    enabled: workspaceId !== null && canInvite,
    placeholderData: (previous) => previous,
  })
}

/** Approving somebody into this workspace, and refreshing both lists it changes. */
export function useApprovePendingUser() {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return useMutation({
    mutationFn: ({
      userId,
      roleSlug,
      projectId,
    }: {
      userId: string
      roleSlug: string
      projectId?: string | undefined
    }) => peopleApi.approvePendingUser(workspaceId as string, userId, { roleSlug, projectId }),
    onSuccess: () => {
      // They left the queue and joined the roster, and may have joined a project.
      void queryClient.invalidateQueries({ queryKey: peopleRoot(workspaceId ?? 'none') })
      void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
    },
  })
}

export function usePeopleMutations() {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId as string

  const invalidate = () => {
    if (!workspaceId) return
    void queryClient.invalidateQueries({ queryKey: peopleRoot(workspaceId) })
  }

  const changeRole = useMutation({
    mutationFn: ({ userId, roleSlug }: { userId: string; roleSlug: string }) =>
      peopleApi.changeMemberRole(workspaceId, userId, roleSlug),
    onSuccess: invalidate,
  })

  const removeMember = useMutation({
    mutationFn: (userId: string) => peopleApi.removeMember(workspaceId, userId),
    onSuccess: () => {
      invalidate()
      // Somebody removed from the workspace leaves every team and project they
      // were on, so those listings are stale too.
      void queryClient.invalidateQueries({ queryKey: queryKeys.teams })
      void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
    },
  })

  return { changeRole, removeMember }
}
