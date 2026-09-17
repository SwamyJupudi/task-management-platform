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
  AccountUser,
  AuthTokens,
} from './types'
import {
  bootstrapSession,
  endSession,
  establishSession,
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
 * Changes the password, and keeps this browser signed in.
 *
 * The endpoint revokes every session — this one included — and hands back a
 * fresh pair so the person changing their password is not thrown out of the
 * browser they are using. `establishSession` is what makes that true: without
 * it the store would still hold an access token the server has just revoked,
 * and the next request would bounce to the sign-in screen.
 *
 * Every *other* session is gone afterwards, which is the point of the design
 * and is why the screen says so before asking.
 *
 * The cache is cleared as well. Nothing in it is wrong, but a password change
 * is the moment somebody expects a clean slate, and the sessions list in
 * particular is now describing a world that no longer exists.
 */
export function useChangePassword(): UseMutationResult<
  void,
  unknown,
  { currentPassword: string; newPassword: string }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (body) => {
      const tokens: AuthTokens = await authApi.changePassword(body)
      await establishSession(tokens)
    },
    onSuccess: () => {
      queryClient.clear()
    },
  })
}

/**
 * Ends every session of this account, including this one.
 *
 * The same shape as {@link useLogout} and for the same reason: the local half
 * runs whether or not the server call succeeded, because somebody who asked to
 * be signed out everywhere must not be left signed in here.
 */
export function useLogoutEverywhere(): UseMutationResult<void, unknown, void> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: authApi.logoutEverywhere,
    onSettled: () => {
      endSession()
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
