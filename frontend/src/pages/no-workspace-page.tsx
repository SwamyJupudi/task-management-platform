import { BuildingIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useLogout } from '@/features/auth'
import { useSessionStore } from '@/stores/session-store'

/**
 * The signed-in account that belongs to no workspace.
 *
 * A real state rather than an error: an invitation can be revoked, and a
 * platform administrator can hold a role without joining anything. Nothing in
 * the shell works without a workspace, so this is shown instead of it, and the
 * only two things the user can actually do from here are offered.
 *
 * Creating a workspace is deliberately not offered. It needs
 * `workspace:create`, which is a platform permission, and the screen that
 * spends it belongs to the workspace feature rather than to the shell.
 */
export function NoWorkspacePage() {
  const logout = useLogout()
  const user = useSessionStore((state) => state.user)

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-5 px-6 text-center">
      <BuildingIcon className="size-8 text-muted-foreground" aria-hidden="true" />
      <div className="space-y-1">
        <h1 className="text-lg font-semibold">You are not in a workspace yet</h1>
        <p className="max-w-sm text-sm text-muted-foreground">
          {user ? `${user.email} has no workspace membership. ` : ''}
          Ask an administrator to invite you, then open the link in the invitation email.
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
