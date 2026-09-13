import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query'
import { useEffect } from 'react'
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { ApiError } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'
import { useSessionStore } from '@/stores/session-store'

import * as authApi from './api'
import type {
  AcceptedInvitation,
  AcceptInvitationBody,
  AccountUser,
  AuthTokens,
  InvitationPreview,
} from './types'
import {
  bootstrapSession,
  endSession,
  establishSession,
  reloadCurrentUser,
} from './session-manager'

/**
 * The authentication feature's hooks.
 *
 * Every write is a TanStack Query mutation, which is what supplies the
 * `isPending`, `isSuccess` and `error` the screens render as their loading,
 * success and error states. None of them catches its own error: the error
 * belongs to the mutation, and the form shows it through `FormError`, so the
 * backend's own message and its request id reach the user unaltered.
 */

/** Runs the one-shot session restore, and reports whether it has finished. */
export function useSessionBootstrap(): boolean {
  const status = useSessionStore((state) => state.status)

  useEffect(() => {
    void bootstrapSession()
  }, [])

  return status !== 'unknown'
}

/**
 * Keeps the store's workspace permission set in step with the active workspace.
 *
 * Mounted once, high in the tree. The route guards read the result and must
 * not issue requests themselves, so the fetching lives here and the guards stay
 * pure reads of the store.
 */
export function useWorkspacePermissionsSync(): void {
  const activeWorkspaceId = useSessionStore((state) => state.activeWorkspaceId)
  const setWorkspacePermissions = useSessionStore((state) => state.setWorkspacePermissions)
  const status = useSessionStore((state) => state.status)

  const { data, isError } = useQuery({
    queryKey: [...queryKeys.auth, 'workspace-permissions', activeWorkspaceId],
    queryFn: () => authApi.workspacePermissions(activeWorkspaceId as string),
    enabled: status === 'authenticated' && activeWorkspaceId !== null,
    // A role edit takes effect on the server immediately; holding a stale copy
    // here would show controls the API has already begun refusing.
    staleTime: 0,
  })

  useEffect(() => {
    if (data) {
      setWorkspacePermissions(data.workspaceId, data.permissions)
      return
    }
    // A failure has to be recorded too, and as an empty set. The guards wait
    // while the answer is unknown, so leaving it unknown after the request has
    // given up would hold every permission-gated route at a spinner for good.
    // Empty means the interface offers nothing it cannot establish a right to,
    // which is the safe direction to be wrong in.
    if (isError && activeWorkspaceId) setWorkspacePermissions(activeWorkspaceId, [])
  }, [data, isError, activeWorkspaceId, setWorkspacePermissions])
}

/**
 * Signs in, then loads the session.
 *
 * Nothing navigates here. `RequireAnonymous` sends an authenticated caller on
 * to the page they originally asked for, which keeps the redirect in one place
 * and avoids racing a route change against the store update that causes it.
 */
export function useLogin(): UseMutationResult<void, unknown, { email: string; password: string }> {
  return useMutation({
    mutationFn: async (credentials) => {
      const tokens: AuthTokens = await authApi.login(credentials)
      await establishSession(tokens)
    },
  })
}

/**
 * Creates an account.
 *
 * No session results. The backend issues no tokens until the address is
 * confirmed, so the screen switches to "check your email" rather than
 * redirecting anywhere.
 */
export function useRegister(): UseMutationResult<
  AccountUser,
  unknown,
  { email: string; password: string; firstName: string; lastName: string }
> {
  return useMutation({ mutationFn: authApi.register })
}

/** Confirms an address using the token from the mailed link. */
export function useVerifyEmail(): UseMutationResult<void, unknown, string> {
  return useMutation({ mutationFn: authApi.verifyEmail })
}

/** Asks for another verification message. Accepted whatever the address is. */
export function useResendVerification(): UseMutationResult<void, unknown, string> {
  return useMutation({ mutationFn: authApi.resendVerification })
}

/** Asks for a reset message. Accepted whatever the address is. */
export function useForgotPassword(): UseMutationResult<void, unknown, string> {
  return useMutation({ mutationFn: authApi.forgotPassword })
}

/** Sets a new password from a reset token. Ends every session server-side. */
export function useResetPassword(): UseMutationResult<
  void,
  unknown,
  { token: string; newPassword: string }
> {
  return useMutation({ mutationFn: authApi.resetPassword })
}

/**
 * What an invitation link points at, before anybody acts on it.
 *
 * A query rather than a one-shot effect, because unlike verifying an address
 * this reads without spending anything: the token stays redeemable, so a
 * refetch or a second mount costs nothing and the screen can offer a retry.
 *
 * It never retries on its own. A rejected token is rejected for a reason that
 * asking again does not change, and the shared client already refuses to retry
 * a 401; this says so at the query as well so a network blip is the only thing
 * that can cause a second request.
 */
export function useInvitationPreview(token: string | null) {
  return useQuery<InvitationPreview>({
    queryKey: [...queryKeys.auth, 'invitation', token],
    queryFn: () => authApi.previewInvitation(token as string),
    enabled: token !== null && token !== '',
    retry: false,
    // The token is single-use and the answer changes the moment it is spent,
    // so nothing here is worth keeping between visits.
    staleTime: 0,
    gcTime: 0,
  })
}

/**
 * Redeems an invitation, and leaves the session describing the new reality.
 *
 * Three steps rather than one, and the order is the point:
 *
 *  1. Accept. The response names the workspace that was joined.
 *  2. Put a session in place. Somebody who already had an account keeps theirs
 *     and only needs `/auth/me` read again, because their membership list has
 *     just changed and nothing else would tell the interface. Somebody whose
 *     account was created by the acceptance has no session at all, so this
 *     signs them in with the credentials they just chose — the account is
 *     created already verified, which is what makes that possible.
 *  3. Drop cached queries. Anything read before this belongs to a smaller set
 *     of workspaces, and the caller is about to be sent into a new one.
 *
 * A failure at step two is not a failure of the acceptance: the person is in
 * the workspace either way. The screen says so and sends them to sign in.
 */
export function useAcceptInvitation(): UseMutationResult<
  AcceptedInvitation & { signedIn: boolean },
  unknown,
  { body: AcceptInvitationBody; authenticated: boolean; signInWith?: string }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ body, authenticated, signInWith }) => {
      const accepted = await authApi.acceptInvitation(body, authenticated)

      if (authenticated) {
        await reloadCurrentUser()
        return { ...accepted, signedIn: true }
      }

      if (signInWith === undefined) return { ...accepted, signedIn: false }

      try {
        const tokens = await authApi.login({ email: signInWith, password: body.password ?? '' })
        await establishSession(tokens)
        return { ...accepted, signedIn: true }
      } catch {
        // The membership is real and committed; only the convenience of
        // arriving signed in was lost. Reported rather than thrown.
        return { ...accepted, signedIn: false }
      }
    },
    onSuccess: () => {
      queryClient.clear()
    },
  })
}

/**
 * Ends the session.
 *
 * The local half runs whether or not the server call succeeds. A network
 * failure must not leave somebody who asked to sign out still signed in, and
 * the refresh cookie is the only thing that could revive the session.
 */
export function useLogout(): UseMutationResult<void, unknown, void> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      endSession()
      // Nothing cached was fetched anonymously. Leaving it would show the
      // previous account's data behind the next sign-in on this tab.
      queryClient.clear()
    },
  })
}

/**
 * Puts a rejected request's field messages back on the fields that caused it.
 *
 * The backend answers a `VALIDATION_ERROR` with an `errors` array naming each
 * field, and those names match the form's, because both mirror the same
 * record. Anything it names that the form does not have is left to
 * `FormError`, which shows the summary message.
 */
export function applyFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): void {
  if (!(error instanceof ApiError)) return
  const violations = error.fieldErrors()
  for (const field of fields) {
    const message = violations[field]
    if (message) setError(field, { type: 'server', message })
  }
}
