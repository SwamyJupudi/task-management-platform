import { useCallback } from 'react'

import { useSessionStore } from '@/stores/session-store'

/**
 * Answers "may the current user do this here?" using the same permission
 * codes the API is gated on, which is the property docs/architecture.md asks
 * for: one catalog drives both the interface and the backend.
 *
 * A platform permission satisfies a check anywhere. A workspace permission
 * satisfies it only inside the active workspace, which is why the two sets
 * are kept apart in the store rather than merged on arrival.
 *
 * This hides controls; it does not secure anything. Every one of these checks
 * is repeated server-side, and the interface is not where access is enforced.
 */
export interface PermissionChecks {
  /** True when the user holds `code` platform-wide or in the active workspace. */
  has: (code: string) => boolean
  /** True when the user holds every one of `codes`. */
  hasAll: (codes: readonly string[]) => boolean
  /** True when the user holds at least one of `codes`. */
  hasAny: (codes: readonly string[]) => boolean
  /** True once the permission set is known, so callers can wait rather than deny. */
  ready: boolean
}

export function usePermissions(): PermissionChecks {
  const status = useSessionStore((state) => state.status)
  const platformPermissions = useSessionStore((state) => state.platformPermissions)
  const workspacePermissions = useSessionStore((state) => state.workspacePermissions)
  const activeWorkspaceId = useSessionStore((state) => state.activeWorkspaceId)
  const workspacePermissionsLoaded = useSessionStore((state) => state.workspacePermissionsLoaded)

  const has = useCallback(
    (code: string) => platformPermissions.includes(code) || workspacePermissions.includes(code),
    [platformPermissions, workspacePermissions],
  )

  const hasAll = useCallback((codes: readonly string[]) => codes.every(has), [has])
  const hasAny = useCallback((codes: readonly string[]) => codes.some(has), [has])

  // Not ready until both halves of the set are known. An account with no
  // membership at all has no workspace half to wait for, so requiring one
  // would hold it at a spinner forever.
  const ready = status !== 'unknown' && (activeWorkspaceId === null || workspacePermissionsLoaded)

  return { has, hasAll, hasAny, ready }
}

/** True when the account holds a platform role, i.e. can reach the admin panel. */
export function useIsPlatformAdmin(): boolean {
  return useSessionStore((state) => state.platformRole !== null)
}
