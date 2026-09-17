import { BuildingIcon, HourglassIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useLogout } from '@/features/auth'
import { useSessionStore } from '@/stores/session-store'

/**
 * The signed-in account that belongs to no workspace.
 *
 * Two readings of the same state, and telling them apart is the point.
 *
 *  - **Waiting for approval.** The ordinary state of a new registration.
 *    Onboarding is by administrator approval: somebody registers, signs in, and
 *    sees this until an administrator admits them to a workspace. Nothing is
 *    wrong and there is nothing for them to do, so the screen says so plainly
 *    rather than implying they have missed a step.
 *  - **Approved, but in no workspace.** Rarer, and a real edge: an account whose
 *    only membership was removed, or a platform administrator who holds a
 *    platform role without belonging to anything.
 *
 * Creating a workspace is deliberately not offered. It needs `workspace:create`,
 * which is a platform permission, and the screen that spends it lives in the
 * admin panel.
 */
export function NoWorkspacePage() {
  const logout = useLogout()
  const user = useSessionStore((state) => state.user)

  const waiting = user?.status === 'PENDING_APPROVAL'

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-5 px-6 text-center">
      {waiting ? (
        <HourglassIcon className="size-8 text-muted-foreground" aria-hidden="true" />
      ) : (
        <BuildingIcon className="size-8 text-muted-foreground" aria-hidden="true" />
      )}

      <div className="space-y-1">
        <h1 className="text-lg font-semibold">
          {waiting ? 'Your account is waiting for approval' : 'You are not in a workspace yet'}
        </h1>
        <p className="max-w-sm text-sm text-muted-foreground">
          {user ? `${user.email} ` : ''}
          {waiting
            ? 'is registered. An administrator has to approve it and choose which workspace you join. You will see your work here once they do.'
            : 'belongs to no workspace. Ask an administrator to add you to one.'}
        </p>
      </div>

      <div className="flex items-center gap-2">
        <Button variant="outline" onClick={() => window.location.reload()}>
          Check again
        </Button>
        <Button variant="ghost" onClick={() => logout.mutate()} disabled={logout.isPending}>
          Sign out
        </Button>
      </div>
    </div>
  )
}
