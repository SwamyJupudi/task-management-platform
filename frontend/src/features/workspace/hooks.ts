import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'

import { reloadCurrentUser } from '@/features/auth'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { queryKeys } from '@/lib/query-client'

import * as workspaceApi from './api'
import type { UpdateWorkspaceInput, Workspace } from './types'

/**
 * The settings screen's server state.
 *
 * Every key carries the workspace id, like every other workspace-scoped feature
 * in the application: two workspaces are two different answers, and a key that
 * left it out would show one workspace's settings under the other's name.
 */

const STALE_MS = 30_000

function workspaceRoot(workspaceId: string) {
  return [...queryKeys.workspaces, workspaceId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may do with this workspace's settings.
 *
 * Codes, and the ordinary workspace-scoped check rather than the platform-only
 * one the admin panel uses. That is right here: these endpoints are guarded per
 * workspace and resolve against the union of a platform role and a membership,
 * so a platform administrator passes them for any workspace and a workspace
 * administrator passes them for their own. Both are legitimate.
 *
 * Reading is separated from editing because the difference is visible: every
 * seeded role holds `workspace:read`, only ADMIN holds the other two. An
 * employee opening this screen sees the settings and no controls, which is more
 * useful than a refusal — the timezone every date on their screen is read in is
 * worth knowing even when you cannot change it.
 */
export interface WorkspacePermissions {
  canRead: boolean
  canUpdate: boolean
  canArchive: boolean
}

export function useWorkspacePermissions(): WorkspacePermissions {
  const { has } = usePermissions()

  return {
    canRead: has('workspace:read'),
    canUpdate: has('workspace:update'),
    canArchive: has('workspace:archive'),
  }
}

// --- queries ----------------------------------------------------------------

/**
 * The active workspace's settings.
 *
 * Fetched rather than read from the session store. The store holds the
 * membership row — a name, a slug and the caller's role — and nothing else;
 * the description, the timezone and the default role exist only here.
 */
export function useWorkspaceSettings(): UseQueryResult<Workspace> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useWorkspacePermissions()

  return useQuery({
    queryKey: [...workspaceRoot(workspaceId ?? 'none'), 'settings'],
    queryFn: () => workspaceApi.getWorkspace(workspaceId as string),
    enabled: workspaceId !== null && canRead,
    staleTime: STALE_MS,
  })
}

// --- mutations --------------------------------------------------------------

/**
 * Editing the workspace, and moving it between its two lifecycle states.
 *
 * Each success does two things beyond refreshing this screen:
 *
 *  - **The session is re-read.** The workspace's name lives in the membership
 *    list `/auth/me` returned, which is what the header, the breadcrumb trail
 *    and the workspace switcher all render from. Renaming without this would
 *    change the settings form and nothing else on screen, which reads as a
 *    failed save.
 *  - **Everything else is invalidated.** Archiving freezes every write in the
 *    workspace, so every screen that offered a control a moment ago is now
 *    showing one the server will refuse; the cheapest correct answer is to let
 *    them all refetch.
 */
export function useWorkspaceMutations() {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId as string

  const settle = async () => {
    await reloadCurrentUser()
    void queryClient.invalidateQueries()
  }

  const update = useMutation({
    mutationFn: (body: UpdateWorkspaceInput) => workspaceApi.updateWorkspace(workspaceId, body),
    onSuccess: settle,
  })

  const archive = useMutation({
    mutationFn: () => workspaceApi.archiveWorkspace(workspaceId),
    onSuccess: settle,
  })

  const unarchive = useMutation({
    mutationFn: () => workspaceApi.unarchiveWorkspace(workspaceId),
    onSuccess: settle,
  })

  return { update, archive, unarchive }
}
