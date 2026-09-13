import { useEffect } from 'react'
import { Navigate, Outlet, useParams } from 'react-router-dom'

import { FullPageSpinner } from '@/components/common/full-page-spinner'
import { NoWorkspacePage } from '@/pages/no-workspace-page'
import { WorkspaceNotFoundPage } from '@/pages/workspace-not-found-page'
import { useSessionStore } from '@/stores/session-store'

import { paths } from './paths'

/**
 * Turns the `:workspaceSlug` in the URL into the session's active workspace.
 *
 * The URL is the authority, not the store. A reload, a bookmark or a second
 * tab all arrive with a slug and no memory of what was last selected, so the
 * route sets the store rather than the other way round; the switcher navigates
 * and lets this run, which keeps one path into the state instead of two.
 *
 * Three outcomes are rendered rather than redirected, because each is a
 * different thing to tell the user: the account belongs to no workspace, the
 * slug is not one of theirs, or the permission set for the workspace they just
 * opened has not arrived yet.
 *
 * Nothing here is a security boundary. Every request the screens inside make
 * carries the workspace id, and the backend answers 404 for a workspace the
 * caller has nothing to do with whatever the URL says.
 */
export function WorkspaceRoute() {
  const { workspaceSlug } = useParams<{ workspaceSlug: string }>()
  const memberships = useSessionStore((state) => state.memberships)
  const activeWorkspaceId = useSessionStore((state) => state.activeWorkspaceId)
  const setActiveWorkspace = useSessionStore((state) => state.setActiveWorkspace)

  const membership = memberships.find((candidate) => candidate.workspaceSlug === workspaceSlug)
  const matched = membership?.workspaceId ?? null
  const settled = matched !== null && matched === activeWorkspaceId

  useEffect(() => {
    if (matched !== null && matched !== activeWorkspaceId) setActiveWorkspace(matched)
  }, [matched, activeWorkspaceId, setActiveWorkspace])

  if (memberships.length === 0) return <NoWorkspacePage />
  if (!membership) return <WorkspaceNotFoundPage slug={workspaceSlug} />

  // The store is one render behind the effect above, and switching workspaces
  // clears the permission set. Rendering the shell now would show the previous
  // workspace's navigation under the new workspace's name.
  if (!settled) return <FullPageSpinner label={`Opening ${membership.workspaceName}`} />

  return <Outlet />
}

/**
 * Where `/` lands: the workspace the session was last in, or the first one.
 *
 * Sign-in deliberately does not choose a workspace. It only fills the store,
 * and the redirect happens here, so there is one place that decides where a
 * signed-in user with no other destination goes.
 */
export function WorkspaceIndexRedirect() {
  const memberships = useSessionStore((state) => state.memberships)
  const activeWorkspaceId = useSessionStore((state) => state.activeWorkspaceId)

  const target =
    memberships.find((membership) => membership.workspaceId === activeWorkspaceId) ?? memberships[0]

  if (!target) return <NoWorkspacePage />

  return <Navigate to={paths.workspace.dashboard(target.workspaceSlug)} replace />
}
