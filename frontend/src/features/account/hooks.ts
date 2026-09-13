import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'

import { reloadCurrentUser } from '@/features/auth'
import { queryKeys } from '@/lib/query-client'
import { useSessionStore } from '@/stores/session-store'

import * as accountApi from './api'
import type { Session, UpdateProfileInput } from './types'

/**
 * The account feature's server state.
 *
 * **No key carries a workspace or a user id**, and both absences are the point.
 * These endpoints describe the signed-in account and take their subject from
 * the security context, so there is exactly one answer per session and nothing
 * to key by. Signing out clears the cache, which is what keeps the next
 * account on this browser from seeing the previous one's sessions.
 */

const accountRoot = [...queryKeys.auth, 'account'] as const

/** The signed-in account, straight from the session store. No request needed. */
export function useCurrentAccount() {
  return useSessionStore((state) => state.user)
}

/**
 * Every live session of this account.
 *
 * Not cached for long and refetched when the window is focused again. A session
 * list is the screen somebody opens because they suspect something, and one
 * that quietly showed a five-minute-old picture would be answering a different
 * question from the one being asked.
 */
export function useSessions(): UseQueryResult<Session[]> {
  return useQuery({
    queryKey: [...accountRoot, 'sessions'],
    queryFn: accountApi.listSessions,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
}

/**
 * Editing your own name.
 *
 * The store is reloaded rather than patched. `/auth/me` is what populated it,
 * the header and the account menu render from it, and re-reading is one line
 * that cannot drift from whatever that endpoint decides to return next.
 */
export function useUpdateProfile() {
  return useMutation({
    mutationFn: async (body: UpdateProfileInput) => {
      const updated = await accountApi.updateOwnProfile(body)
      await reloadCurrentUser()
      return updated
    },
  })
}

/**
 * Ending one session.
 *
 * Only the list is invalidated. Revoking a session other than this one changes
 * nothing else on the screen, and revoking *this* one is a case the interface
 * deliberately does not offer — the row for the current session has no button,
 * because "sign out" and "sign out everywhere" are the two controls that mean
 * that, and both of them also clear the session locally.
 */
export function useRevokeSession() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (sessionId: string) => accountApi.revokeSession(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...accountRoot, 'sessions'] })
    },
  })
}
