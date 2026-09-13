import { useSessionStore } from '@/stores/session-store'
import type { Membership } from '@/types/session'

/**
 * The workspace the interface is currently showing, as a membership row.
 *
 * The store holds only the identifier; the name, the slug and the role that go
 * with it come from the membership list `/auth/me` already returned, so the
 * shell needs no request of its own to render a workspace name or to build a
 * workspace-scoped link.
 *
 * Null means there is no active workspace: either the account belongs to none,
 * or the session has not loaded yet. Both are states the shell has to render
 * rather than assume away.
 */
export function useActiveWorkspace(): Membership | null {
  const memberships = useSessionStore((state) => state.memberships)
  const activeWorkspaceId = useSessionStore((state) => state.activeWorkspaceId)

  return memberships.find((membership) => membership.workspaceId === activeWorkspaceId) ?? null
}

/**
 * The active workspace's slug, for building links.
 *
 * A separate hook because most callers want only the slug, and subscribing to
 * the membership row would re-render them when a workspace is renamed.
 */
export function useActiveWorkspaceSlug(): string | null {
  return useActiveWorkspace()?.workspaceSlug ?? null
}
