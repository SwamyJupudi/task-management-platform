import { create } from 'zustand'

import type { CurrentUser, Membership, User } from '@/types/session'

/**
 * The session and the permission set, in the small store
 * docs/architecture.md calls for.
 *
 * It holds state and nothing else: no fetching, no token refresh, no
 * sign-in. The authentication feature owns those and drives this store
 * through the setters below. Keeping the network out of here is what lets
 * the layout and the route guards read a session without importing a
 * feature.
 *
 * The access token is held in memory on purpose. It is never written to
 * `localStorage`, where any injected script could read it; the long-lived
 * credential is the httpOnly refresh cookie, which JavaScript cannot reach.
 */

export type SessionStatus =
  /** Before the first `/auth/me` call has resolved. Render nothing decisive. */
  | 'unknown'
  | 'authenticated'
  | 'anonymous'

interface SessionState {
  status: SessionStatus
  user: User | null
  platformRole: string | null
  /** Permission codes held platform-wide, from `/auth/me`. */
  platformPermissions: string[]
  memberships: Membership[]
  /** The workspace the interface is currently showing. */
  activeWorkspaceId: string | null
  /**
   * Permission codes held in the active workspace, from
   * `GET /workspaces/{id}/me`. Empty until that call resolves, so a guard
   * should wait on `status` rather than treat empty as "denied".
   */
  workspacePermissions: string[]
  accessToken: string | null
}

interface SessionActions {
  setAccessToken: (token: string | null) => void
  setCurrentUser: (current: CurrentUser) => void
  setWorkspacePermissions: (workspaceId: string, permissions: string[]) => void
  setActiveWorkspace: (workspaceId: string | null) => void
  markAnonymous: () => void
  clear: () => void
}

const initialState: SessionState = {
  status: 'unknown',
  user: null,
  platformRole: null,
  platformPermissions: [],
  memberships: [],
  activeWorkspaceId: null,
  workspacePermissions: [],
  accessToken: null,
}

export const useSessionStore = create<SessionState & SessionActions>()((set, get) => ({
  ...initialState,

  setAccessToken: (accessToken) => set({ accessToken }),

  setCurrentUser: (current) =>
    set((state) => ({
      status: 'authenticated',
      user: current.user,
      platformRole: current.platformRole,
      platformPermissions: current.platformPermissions,
      memberships: current.memberships,
      // Keep the chosen workspace if it is still one of theirs, otherwise
      // fall back to the first. Membership can be revoked between sessions.
      activeWorkspaceId:
        current.memberships.find((m) => m.workspaceId === state.activeWorkspaceId)?.workspaceId ??
        current.memberships[0]?.workspaceId ??
        null,
    })),

  setWorkspacePermissions: (workspaceId, permissions) => {
    // Ignore a response that arrived after the user switched away, which
    // would otherwise grant one workspace's permissions inside another.
    if (get().activeWorkspaceId !== workspaceId) return
    set({ workspacePermissions: permissions })
  },

  setActiveWorkspace: (activeWorkspaceId) =>
    set({ activeWorkspaceId, workspacePermissions: [] }),

  markAnonymous: () => set({ ...initialState, status: 'anonymous' }),

  clear: () => set({ ...initialState, status: 'anonymous' }),
}))

/** Reads the token without subscribing, for the API client's token provider. */
export function getAccessToken(): string | null {
  return useSessionStore.getState().accessToken
}
