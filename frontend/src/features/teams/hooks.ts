import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'
import { useSessionStore } from '@/stores/session-store'

import * as teamsApi from './api'
import type { CreateTeamInput, Team, TeamMember, TeamStatus, UpdateTeamInput } from './types'

/**
 * The teams feature's server state.
 *
 * Every key carries the workspace id, for the reason every other feature's
 * does: two workspaces are two different sets of teams.
 */

const STALE_MS = 60_000
const PAGE_SIZE = 20

function teamsRoot(workspaceId: string) {
  return [...queryKeys.teams, workspaceId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may do with teams in the active workspace.
 *
 * Codes, never role names. Deleting is the one to notice: `team:delete` is the
 * workspace administrator's and not a lead's, so a lead may rename, archive and
 * staff their own team without being able to remove it.
 */
export interface TeamPermissions {
  canRead: boolean
  canCreate: boolean
  canUpdate: boolean
  canDelete: boolean
  canManageMembers: boolean
  /** True when the caller may act on every team, not only the ones they lead. */
  manageAny: boolean
}

export function useTeamPermissions(): TeamPermissions {
  const { has } = usePermissions()

  return {
    canRead: has('team:read'),
    canCreate: has('team:create'),
    canUpdate: has('team:update'),
    canDelete: has('team:delete'),
    canManageMembers: has('team:manage_members'),
    manageAny: has('team:manage_any'),
  }
}

/**
 * Whether the caller may change one particular team.
 *
 * Mirrors `TeamAccessGuard.requireChangeableTeam`: holding the code is
 * necessary, and on top of it either `team:manage_any` or leading this team.
 *
 * Unlike the project and task equivalents, this one is exact — a team carries
 * its own lead, so nothing has to be fetched to answer it and no case is left
 * unanswered.
 */
export function useTeamAbilities(team: Team | undefined) {
  const permissions = useTeamPermissions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  const leadsIt = userId !== null && team?.leadUserId === userId
  const reaches = permissions.manageAny || leadsIt

  return {
    canEdit: permissions.canUpdate && reaches,
    canArchive: permissions.canUpdate && reaches,
    canDelete: permissions.canDelete && reaches,
    canManageMembers: permissions.canManageMembers && reaches,
    leadsIt,
  }
}

// --- queries ----------------------------------------------------------------

export function useTeams(status: TeamStatus | undefined, page: number): UseQueryResult<Page<Team>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useTeamPermissions()

  return useQuery({
    queryKey: [...teamsRoot(workspaceId ?? 'none'), 'list', status ?? 'any', page],
    queryFn: () => teamsApi.listTeams(workspaceId as string, status, page, PAGE_SIZE),
    enabled: workspaceId !== null && canRead,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

export function useTeam(teamId: string | undefined): UseQueryResult<Team> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useTeamPermissions()

  return useQuery({
    queryKey: [...teamsRoot(workspaceId ?? 'none'), 'detail', teamId],
    queryFn: () => teamsApi.getTeam(workspaceId as string, teamId as string),
    enabled: workspaceId !== null && teamId !== undefined && canRead,
    staleTime: STALE_MS,
  })
}

export function useTeamMembers(teamId: string | undefined): UseQueryResult<Page<TeamMember>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useTeamPermissions()

  return useQuery({
    queryKey: [...teamsRoot(workspaceId ?? 'none'), 'members', teamId],
    queryFn: () => teamsApi.listTeamMembers(workspaceId as string, teamId as string),
    enabled: workspaceId !== null && teamId !== undefined && canRead,
    staleTime: STALE_MS,
  })
}

// --- mutations --------------------------------------------------------------

/**
 * Every team write, sharing one invalidation.
 *
 * Broad on purpose. A team's name, status and lead all appear on the listing,
 * on the detail screen and in the project filters, so a narrower invalidation
 * would leave one of them showing the old answer.
 */
export function useTeamMutations(teamId?: string) {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId as string

  const invalidate = () => {
    if (!workspaceId) return
    void queryClient.invalidateQueries({ queryKey: teamsRoot(workspaceId) })
    // Projects carry their team's name, and the project filters are built from
    // the team list.
    void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
  }

  const create = useMutation({
    mutationFn: (body: CreateTeamInput) => teamsApi.createTeam(workspaceId, body),
    onSuccess: invalidate,
  })

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTeamInput }) =>
      teamsApi.updateTeam(workspaceId, id, body),
    onSuccess: invalidate,
  })

  const setArchived = useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      archived ? teamsApi.archiveTeam(workspaceId, id) : teamsApi.unarchiveTeam(workspaceId, id),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: string) => teamsApi.deleteTeam(workspaceId, id),
    onSuccess: invalidate,
  })

  const addMember = useMutation({
    mutationFn: (userId: string) => teamsApi.addTeamMember(workspaceId, teamId as string, userId),
    onSuccess: invalidate,
  })

  const removeMember = useMutation({
    mutationFn: (userId: string) =>
      teamsApi.removeTeamMember(workspaceId, teamId as string, userId),
    onSuccess: invalidate,
  })

  const setLead = useMutation({
    // Null clears it, which is a DELETE on the same address rather than a null
    // identifier in the body.
    mutationFn: (userId: string | null) =>
      userId === null
        ? teamsApi.clearLead(workspaceId, teamId as string)
        : teamsApi.assignLead(workspaceId, teamId as string, userId),
    onSuccess: invalidate,
  })

  return { create, update, setArchived, remove, addMember, removeMember, setLead }
}
