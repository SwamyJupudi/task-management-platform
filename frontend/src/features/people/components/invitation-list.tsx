import { SendIcon, XIcon } from 'lucide-react'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { toUserMessage } from '@/lib/api'
import { cn } from '@/lib/utils'

import { usePeopleMutations, usePeoplePermissions } from '../hooks'
import type { Invitation } from '../types'

/**
 * Invitations, outstanding and settled.
 *
 * Two actions, and only one of them is an endpoint. Withdrawing calls
 * `DELETE /invitations/{id}`. "Send again" is a fresh invite to the same
 * address, because the platform has no resend: a new invitation supersedes the
 * outstanding one. The button says "Send again" rather than "Resend" for that
 * reason — it is a new invitation, with a new expiry.
 *
 * Both are offered only on an invitation still waiting to be accepted. A
 * settled one is history, and re-inviting somebody who has already joined is
 * something the backend decides about rather than something to offer.
 */

function formatWhen(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

/** True once the expiry has passed, which the backend enforces on acceptance. */
function hasExpired(invitation: Invitation): boolean {
  const expires = new Date(invitation.expiresAt).getTime()
  return !Number.isNaN(expires) && expires < Date.now()
}

function InvitationActions({ invitation }: { invitation: Invitation }) {
  const permissions = usePeoplePermissions()
  const { sendInvite, revokeInvite } = usePeopleMutations()

  if (!permissions.canInvite || invitation.status !== 'PENDING') return null

  const busy = sendInvite.isPending || revokeInvite.isPending

  return (
    <span className="flex items-center justify-end gap-0.5">
      <Button
        variant="ghost"
        size="icon-sm"
        disabled={busy}
        aria-label={`Send another invitation to ${invitation.email}`}
        onClick={async () => {
          try {
            await sendInvite.mutateAsync({
              email: invitation.email,
              roleSlug: invitation.roleSlug,
            })
            toast.success(`A fresh invitation was sent to ${invitation.email}.`)
          } catch (error) {
            toast.error(toUserMessage(error))
          }
        }}
      >
        <SendIcon aria-hidden="true" />
      </Button>

      <Button
        variant="ghost"
        size="icon-sm"
        disabled={busy}
        aria-label={`Withdraw the invitation to ${invitation.email}`}
        onClick={async () => {
          try {
            await revokeInvite.mutateAsync(invitation.id)
            toast.success('Invitation withdrawn.')
          } catch (error) {
            toast.error(toUserMessage(error))
          }
        }}
      >
        <XIcon aria-hidden="true" />
      </Button>
    </span>
  )
}

function StatusBadge({ invitation }: { invitation: Invitation }) {
  if (invitation.status !== 'PENDING') {
    return <Badge variant="secondary">{invitation.status.toLowerCase()}</Badge>
  }
  if (hasExpired(invitation)) {
    return (
      <Badge variant="outline" className="border-destructive/40 text-destructive">
        expired
      </Badge>
    )
  }
  return <Badge variant="outline">pending</Badge>
}

export function InvitationList({ invitations }: { invitations: Invitation[] }) {
  return (
    <>
      {/* Cards below md. */}
      <ul className="space-y-3 md:hidden">
        {invitations.map((invitation) => (
          <li key={invitation.id} className="rounded-lg border border-border p-4">
            <div className="flex items-start justify-between gap-2">
              <span className="min-w-0 truncate text-sm font-medium">{invitation.email}</span>
              <StatusBadge invitation={invitation} />
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {invitation.roleSlug} · invited {formatWhen(invitation.createdAt)} · expires{' '}
              {formatWhen(invitation.expiresAt)}
            </p>
            <div className="mt-2 flex justify-end">
              <InvitationActions invitation={invitation} />
            </div>
          </li>
        ))}
      </ul>

      {/* Table from md. */}
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Address</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Invited</TableHead>
              <TableHead className="text-right">Expires</TableHead>
              <TableHead className="w-20" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {invitations.map((invitation) => (
              <TableRow key={invitation.id}>
                <TableCell className="max-w-[18rem] truncate font-medium">
                  {invitation.email}
                </TableCell>
                <TableCell className="text-muted-foreground">{invitation.roleSlug}</TableCell>
                <TableCell>
                  <StatusBadge invitation={invitation} />
                </TableCell>
                <TableCell className="text-right whitespace-nowrap text-muted-foreground">
                  {formatWhen(invitation.createdAt)}
                </TableCell>
                <TableCell
                  className={cn(
                    'text-right whitespace-nowrap',
                    hasExpired(invitation) && invitation.status === 'PENDING'
                      ? 'text-destructive'
                      : 'text-muted-foreground',
                  )}
                >
                  {formatWhen(invitation.expiresAt)}
                </TableCell>
                <TableCell className="text-right">
                  <InvitationActions invitation={invitation} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </>
  )
}
