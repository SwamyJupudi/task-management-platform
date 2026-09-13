import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { relativeTime } from '@/lib/datetime'
import { cn } from '@/lib/utils'

import { humanise } from '../constants'
import { useCurrentUserId } from '../hooks'
import type { PlatformAccount } from '../types'
import { AccountActions } from './account-actions'

/**
 * The administrative account directory.
 *
 * Status and lock are separate columns because they are separate facts. A
 * deactivation is a durable human decision; a lockout is a temporary machine
 * one that expires on its own. Collapsing them into one "state" column would
 * lose the distinction an administrator opens this screen to resolve, which is
 * usually "why can this person not sign in".
 *
 * A lock is checked against the clock rather than trusted: `lockedUntil` can be
 * in the past on a row fetched a minute ago, and a row that still said "locked"
 * after the lock expired would be wrong in the direction that causes support
 * tickets.
 *
 * The signed-in account's own row is marked. Every action on it still works —
 * the backend decides what is allowed — but knowing which row is yours before
 * you deactivate it is worth a badge.
 */
export function AccountTable({ accounts }: { accounts: PlatformAccount[] }) {
  const currentUserId = useCurrentUserId()

  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Account</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Sign-in</TableHead>
            <TableHead className="text-right">Workspaces</TableHead>
            <TableHead>Last seen</TableHead>
            <TableHead className="w-12 text-right">
              <span className="sr-only">Actions</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {accounts.map((account) => {
            const locked =
              account.lockedUntil !== null && new Date(account.lockedUntil) > new Date()
            const isSelf = currentUserId !== null && currentUserId === account.id

            return (
              <TableRow key={account.id}>
                <TableCell className="max-w-[20rem]">
                  <span className="flex flex-wrap items-center gap-1.5">
                    <span className="truncate font-medium">
                      {account.firstName} {account.lastName}
                    </span>
                    {account.platformAdministrator ? (
                      <Badge variant="secondary">Platform admin</Badge>
                    ) : null}
                    {isSelf ? <Badge variant="outline">You</Badge> : null}
                  </span>
                  <span className="block truncate text-xs text-muted-foreground">
                    {account.email}
                  </span>
                </TableCell>

                <TableCell>
                  <Badge
                    variant="outline"
                    className={cn(account.status === 'DEACTIVATED' && 'text-muted-foreground')}
                  >
                    {humanise(account.status)}
                  </Badge>
                </TableCell>

                <TableCell className="space-y-1">
                  {locked ? (
                    <Badge variant="destructive">
                      Locked until {new Date(account.lockedUntil as string).toLocaleString()}
                    </Badge>
                  ) : (
                    <span className="text-xs text-muted-foreground">Not locked</span>
                  )}
                  {account.emailVerified ? null : (
                    <span className="block text-xs text-muted-foreground">Address unconfirmed</span>
                  )}
                </TableCell>

                <TableCell className="text-right tabular-nums">
                  {account.workspaceCount.toLocaleString()}
                </TableCell>

                <TableCell className="text-xs whitespace-nowrap text-muted-foreground">
                  {account.lastLoginAt === null ? (
                    'Never'
                  ) : (
                    <time
                      dateTime={account.lastLoginAt}
                      title={new Date(account.lastLoginAt).toLocaleString()}
                    >
                      {relativeTime(account.lastLoginAt)}
                    </time>
                  )}
                </TableCell>

                <TableCell className="text-right">
                  <AccountActions account={account} />
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}
