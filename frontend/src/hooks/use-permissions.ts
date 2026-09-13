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
 * **The admin panel needs the narrower question**, and that is what
 * `hasOnPlatform` is for. Every endpoint under `/admin` is gated by
 * `@perm.onPlatform`, whose resolver reads the caller's platform role and never
 * consults workspace membership — administering one workspace must not become a
 * way to act across the installation. Several codes the panel uses are held by
 * the seeded workspace administrator too (`user:read`, `role:read`,
 * `role:manage`, `permission:read`, `activity:read`), so asking the union there
 * would offer a workspace administrator controls the API answers 403 to.
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
  /**
   * True when the user holds `code` through a platform role.
   *
   * The mirror of the backend's `@perm.onPlatform`. A workspace grant never
   * satisfies it, however wide that grant is.
   */
  hasOnPlatform: (code: string) => boolean
  /** True when the user holds every one of `codes` through a platform role. */
  hasAllOnPlatform: (codes: readonly string[]) => boolean
  /** True when the user holds at least one of `codes` through a platform role. */
  hasAnyOnPlatform: (codes: readonly string[]) => boolean
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

  // Deliberately does not consult `workspacePermissions`. This is the whole
  // difference between the two families of check, and merging them here would
  // silently undo the separation the store exists to keep.
  const hasOnPlatform = useCallback(
    (code: string) => platformPermissions.includes(code),
    [platformPermissions],
  )

  const hasAllOnPlatform = useCallback(
    (codes: readonly string[]) => codes.every(hasOnPlatform),
    [hasOnPlatform],
  )

  const hasAnyOnPlatform = useCallback(
    (codes: readonly string[]) => codes.some(hasOnPlatform),
    [hasOnPlatform],
  )

  // Not ready until both halves of the set are known. An account with no
  // membership at all has no workspace half to wait for, so requiring one
  // would hold it at a spinner forever.
  const ready = status !== 'unknown' && (activeWorkspaceId === null || workspacePermissionsLoaded)

  return { has, hasAll, hasAny, hasOnPlatform, hasAllOnPlatform, hasAnyOnPlatform, ready }
}

/** True when the account holds a platform role, i.e. can reach the admin panel. */
export function useIsPlatformAdmin(): boolean {
  return useSessionStore((state) => state.platformRole !== null)
}
