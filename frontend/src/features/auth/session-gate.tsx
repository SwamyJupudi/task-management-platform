import { useQueryClient } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'

import { useSessionStore } from '@/stores/session-store'

import { useSessionBootstrap, useWorkspacePermissionsSync } from './hooks'

/**
 * Drives the session for the whole application.
 *
 * Three jobs, all of which have to happen exactly once and above every route:
 *
 *  - Restore the session on load. The access token is in memory, so a reload
 *    loses it; the refresh cookie is spent once to get a new one. Until that
 *    resolves the store says `unknown` and the guards hold at a spinner, which
 *    is what stops a reload bouncing a signed-in user to the sign-in screen.
 *  - Keep the active workspace's permission set loaded, because the route
 *    guards read it and must not fetch anything themselves.
 *  - Empty the query cache when a session ends.
 *
 * It renders its children immediately rather than blocking on the bootstrap.
 * The public screens have nothing to wait for, and the private ones already
 * wait inside `RequireAuth`, so gating the whole tree here would only add a
 * second spinner in front of the first.
 */
export function SessionGate({ children }: { children: ReactNode }) {
  useSessionBootstrap()
  useWorkspacePermissionsSync()

  const status = useSessionStore((state) => state.status)
  const queryClient = useQueryClient()

  useEffect(() => {
    // Covers the sign-outs that do not go through `useLogout`: an expired
    // refresh token, or a session revoked elsewhere. Nothing cached was
    // fetched anonymously, so leaving it would show one account's data to
    // whoever signs in next on this tab.
    if (status === 'anonymous') queryClient.clear()
  }, [status, queryClient])

  return <>{children}</>
}
