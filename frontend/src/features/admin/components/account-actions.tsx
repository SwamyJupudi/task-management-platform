import { MoreHorizontalIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { toUserMessage } from '@/lib/api'

import { useAccountMutations, useAdminPermissions, useCurrentUserId } from '../hooks'
import type { PlatformAccount } from '../types'
import { AccountEditDialog } from './account-edit-dialog'

/**
 * Everything an administrator may do to one account.
 *
 * Each entry appears only when the signed-in account holds the platform code
 * its endpoint is gated on, and only when it would do something: an active
 * account is not offered activation, an unlocked one is not offered unlocking,
 * and a confirmed address is not offered another verification message, which
 * the backend refuses outright.
 *
 * **Three things are deliberately not pre-empted.** Revoking the platform role
 * from the last remaining administrator, and from yourself, are both refused
 * with 409 by the backend; the control stays and the message is shown, because
 * hiding it would mean counting administrators here and being wrong the moment
 * somebody made another. Your own row is the exception that *is* pre-empted —
 * the panel marks it, since an administrator locking themselves out of their
 * own account by accident is a different class of mistake.
 *
 * The two destructive-feeling actions — deactivating an account and revoking
 * the platform role — are confirmed. The rest are single, reversible calls and
 * a confirmation on each would train people to click through them.
 */
export function AccountActions({ account }: { account: PlatformAccount }) {
  const permissions = useAdminPermissions()
  const currentUserId = useCurrentUserId()
  const mutations = useAccountMutations()

  const [editing, setEditing] = useState(false)
  const [deactivating, setDeactivating] = useState(false)
  const [revoking, setRevoking] = useState(false)

  const isSelf = currentUserId !== null && currentUserId === account.id
  const locked = account.lockedUntil !== null && new Date(account.lockedUntil) > new Date()

  /** One place for "call it, say what happened, say why not". */
  const run = async (action: Promise<unknown>, success: string) => {
    try {
      await action
      toast.success(success)
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  }

  const canActivate = permissions.canActivateAccounts && account.status === 'DEACTIVATED'
  const canDeactivate = permissions.canDeactivateAccounts && account.status !== 'DEACTIVATED'
  const canResend = permissions.canUpdateAccounts && !account.emailVerified

  const nothingOffered =
    !permissions.canUpdateAccounts &&
    !canActivate &&
    !canDeactivate &&
    !permissions.canAssignPlatformRole

  if (nothingOffered) return null

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="sm" aria-label={`Actions for ${account.email}`}>
            <MoreHorizontalIcon aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="w-60">
          <DropdownMenuLabel className="truncate">{account.email}</DropdownMenuLabel>

          {permissions.canUpdateAccounts ? (
            <DropdownMenuItem onSelect={() => setEditing(true)}>Edit name</DropdownMenuItem>
          ) : null}

          {permissions.canUpdateAccounts && locked ? (
            <DropdownMenuItem
              onSelect={() =>
                void run(mutations.unlock.mutateAsync(account.id), 'The lockout was cleared.')
              }
            >
              Clear sign-in lockout
            </DropdownMenuItem>
          ) : null}

          {permissions.canUpdateAccounts ? (
            <DropdownMenuItem
              onSelect={() =>
                void run(
                  mutations.startPasswordReset.mutateAsync(account.id),
                  'A recovery link was sent to the account’s own address.',
                )
              }
            >
              Send a password recovery link
            </DropdownMenuItem>
          ) : null}

          {canResend ? (
            <DropdownMenuItem
              onSelect={() =>
                void run(
                  mutations.resendVerification.mutateAsync(account.id),
                  'Another verification message was sent.',
                )
              }
            >
              Resend verification
            </DropdownMenuItem>
          ) : null}

          {canActivate || canDeactivate ? <DropdownMenuSeparator /> : null}

          {canActivate ? (
            <DropdownMenuItem
              onSelect={() =>
                void run(
                  mutations.activate.mutateAsync(account.id),
                  'The account was switched back on.',
                )
              }
            >
              Activate account
            </DropdownMenuItem>
          ) : null}

          {canDeactivate ? (
            <DropdownMenuItem variant="destructive" onSelect={() => setDeactivating(true)}>
              Deactivate account
            </DropdownMenuItem>
          ) : null}

          {permissions.canAssignPlatformRole ? (
            <>
              <DropdownMenuSeparator />
              {account.platformAdministrator ? (
                <DropdownMenuItem variant="destructive" onSelect={() => setRevoking(true)}>
                  Revoke platform administrator
                </DropdownMenuItem>
              ) : (
                <DropdownMenuItem
                  onSelect={() =>
                    void run(
                      mutations.grantPlatformRole.mutateAsync(account.id),
                      `${account.email} is now a platform administrator.`,
                    )
                  }
                >
                  Make platform administrator
                </DropdownMenuItem>
              )}
            </>
          ) : null}
        </DropdownMenuContent>
      </DropdownMenu>

      <AccountEditDialog account={account} open={editing} onOpenChange={setEditing} />

      <AlertDialog open={deactivating} onOpenChange={setDeactivating}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Deactivate {account.email}?</AlertDialogTitle>
            <AlertDialogDescription>
              They stop being able to sign in, and their existing sessions end at their next
              request. Their work, memberships and history are untouched, and switching the account
              back on restores everything.
              {isSelf ? ' This is your own account.' : ''}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={mutations.deactivate.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={mutations.deactivate.isPending}
              onClick={(event) => {
                // The mutation decides when the dialog closes, so a refusal
                // leaves it up with its message rather than vanishing.
                event.preventDefault()
                void mutations.deactivate
                  .mutateAsync(account.id)
                  .then(() => {
                    toast.success('The account was switched off.')
                    setDeactivating(false)
                  })
                  .catch((error: unknown) => toast.error(toUserMessage(error)))
              }}
            >
              {mutations.deactivate.isPending ? 'Deactivating…' : 'Deactivate'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={revoking} onOpenChange={setRevoking}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Revoke platform administrator?</AlertDialogTitle>
            <AlertDialogDescription>
              {account.email} keeps their workspace memberships and loses every platform-wide
              capability, including this panel. Revoking your own, or the last remaining
              administrator, is refused.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={mutations.revokePlatformRole.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={mutations.revokePlatformRole.isPending}
              onClick={(event) => {
                event.preventDefault()
                void mutations.revokePlatformRole
                  .mutateAsync(account.id)
                  .then(() => {
                    toast.success('The platform role was revoked.')
                    setRevoking(false)
                  })
                  .catch((error: unknown) => toast.error(toUserMessage(error)))
              }}
            >
              {mutations.revokePlatformRole.isPending ? 'Revoking…' : 'Revoke'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
