import { LaptopIcon, LogOutIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useLogoutEverywhere } from '@/features/auth'
import { toUserMessage } from '@/lib/api'
import { relativeTime } from '@/lib/datetime'
import { cn } from '@/lib/utils'

import { AccountShell } from '../components/account-shell'
import { useRevokeSession, useSessions } from '../hooks'
import type { Session } from '../types'

/**
 * Where this account is signed in, and how to end it.
 *
 * The screen somebody opens because they suspect something, which is why the
 * list refetches when the window is focused again rather than serving a cached
 * picture of five minutes ago.
 *
 * **The current session has no revoke button**, and that is deliberate rather
 * than an omission. Ending the session you are using is what "sign out" means,
 * and it lives in the account menu where people look for it; a second control
 * that did the same thing from here would leave the interface holding a token
 * the server had just revoked, and the difference between the two would be
 * invisible until the next request failed. Which row is current is the server's
 * answer, computed from the refresh cookie — the browser cannot read it.
 *
 * Signing out everywhere ends this session too. There is no variant that spares
 * it: that is what revoking the others one at a time is for.
 */
export function AccountSessionsPage() {
  const sessions = useSessions()
  const revoke = useRevokeSession()
  const logoutEverywhere = useLogoutEverywhere()

  const [confirming, setConfirming] = useState(false)

  const rows = sessions.data ?? []
  const others = rows.filter((session) => !session.current).length

  return (
    <AccountShell title="Your account" description="How you appear to everybody you work with">
      <Card>
        <CardHeader>
          <CardTitle>Active sessions</CardTitle>
          <CardDescription>
            Every browser this account is currently signed in on. A session ends when it is revoked,
            when you sign out, or when it expires.
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          {sessions.isError ? (
            <ErrorState error={sessions.error} onRetry={() => void sessions.refetch()} />
          ) : sessions.isPending ? (
            <LoadingState label="Loading your sessions" />
          ) : rows.length === 0 ? (
            // Not reachable in practice — reading this list needs a session, so
            // there is always at least one. Rendered anyway rather than crashing
            // on an empty array.
            <EmptyState icon={LaptopIcon} title="No active sessions" />
          ) : (
            <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
              {rows.map((session) => (
                <SessionRow
                  key={session.sessionId}
                  session={session}
                  pending={revoke.isPending}
                  onRevoke={() => {
                    revoke.mutate(session.sessionId, {
                      onSuccess: () => toast.success('That session was ended.'),
                      onError: (error) => toast.error(toUserMessage(error)),
                    })
                  }}
                />
              ))}
            </ul>
          )}

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-muted-foreground">
              {others === 0
                ? 'This is your only active session.'
                : `${others.toLocaleString()} other ${others === 1 ? 'session' : 'sessions'}.`}
            </p>

            <Button
              variant="outline"
              size="sm"
              disabled={logoutEverywhere.isPending || sessions.isPending}
              onClick={() => setConfirming(true)}
            >
              <LogOutIcon aria-hidden="true" />
              Sign out everywhere
            </Button>
          </div>
        </CardContent>
      </Card>

      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Sign out of every session?</AlertDialogTitle>
            <AlertDialogDescription>
              Every browser signed in as this account is signed out, including this one. You will
              need to sign in again. Nothing about your account or your work changes.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={logoutEverywhere.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={logoutEverywhere.isPending}
              onClick={(event) => {
                // Nothing navigates from here. The session ends, the store flips
                // to anonymous, and `RequireAuth` performs the redirect — one
                // redirect, decided in one place, exactly as signing out does.
                event.preventDefault()
                logoutEverywhere.mutate()
              }}
            >
              {logoutEverywhere.isPending ? 'Signing out…' : 'Sign out everywhere'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AccountShell>
  )
}

/**
 * One session.
 *
 * The user agent is rendered as it arrived, truncated. Parsing it into "Chrome
 * on Windows" would need a library and would be wrong often enough to mislead
 * exactly when it mattered; the raw string is ugly and honest.
 */
function SessionRow({
  session,
  pending,
  onRevoke,
}: {
  session: Session
  pending: boolean
  onRevoke: () => void
}) {
  return (
    <li className={cn('flex items-start gap-3 px-3 py-3', session.current && 'bg-primary/[0.04]')}>
      <LaptopIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />

      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-medium">
            {session.ipAddress ?? 'Unknown address'}
          </span>
          {session.current ? <Badge variant="secondary">This browser</Badge> : null}
        </div>

        <p
          className="truncate text-xs text-muted-foreground"
          title={session.userAgent ?? undefined}
        >
          {session.userAgent ?? 'Unknown browser'}
        </p>

        <p className="text-xs text-muted-foreground">
          Started{' '}
          <time dateTime={session.startedAt} title={new Date(session.startedAt).toLocaleString()}>
            {relativeTime(session.startedAt)}
          </time>
          {' · expires '}
          <time dateTime={session.expiresAt} title={new Date(session.expiresAt).toLocaleString()}>
            {new Date(session.expiresAt).toLocaleDateString()}
          </time>
        </p>
      </div>

      {session.current ? (
        <span className="shrink-0 self-center text-xs text-muted-foreground">Sign out to end</span>
      ) : (
        <Button
          variant="ghost"
          size="sm"
          className="shrink-0 self-center"
          disabled={pending}
          onClick={onRevoke}
        >
          Revoke
        </Button>
      )}
    </li>
  )
}
