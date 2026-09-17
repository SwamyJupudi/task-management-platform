import { UserCheckIcon } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { relativeTime } from '@/lib/datetime'

import { ApproveUserDialog } from '../components/approve-user-dialog'
import { usePendingAccounts } from '../hooks'
import type { PlatformAccount } from '../types'

/**
 * Who is waiting to be let in.
 *
 * The queue onboarding by approval created. Somebody registers, appears here,
 * and an administrator decides which workspace they belong to and what they may
 * do there. Nothing has to be delivered to their address for any of it, which is
 * the whole reason this replaced onboarding by invitation.
 *
 * Oldest first, against the newest-first default of every other list in this
 * panel, because the person who has been waiting longest is the one to deal with
 * next.
 */
export function PendingUsersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [approving, setApproving] = useState<PlatformAccount | null>(null)

  const pageIndex = Math.max(Number(searchParams.get('page') ?? '1') - 1, 0)
  const pending = usePendingAccounts(pageIndex)
  const page = pending.data

  const goToPage = (next: number) => {
    const params = new URLSearchParams(searchParams)
    params.set('page', String(next + 1))
    setSearchParams(params, { replace: true })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Pending users"
        description="People who have registered and are waiting to be admitted to a workspace."
      />

      {pending.isError ? (
        <ErrorState error={pending.error} onRetry={() => void pending.refetch()} />
      ) : pending.isPending ? (
        <LoadingState label="Loading the queue" />
      ) : page && page.content.length === 0 ? (
        <EmptyState
          icon={UserCheckIcon}
          title="Nobody is waiting"
          description="New registrations appear here until an administrator approves them into a workspace."
        />
      ) : page ? (
        <>
          <div className="rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Registered</TableHead>
                  <TableHead className="w-0 text-right">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {page.content.map((account) => (
                  <TableRow key={account.id}>
                    <TableCell className="font-medium">
                      {account.firstName} {account.lastName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{account.email}</TableCell>
                    <TableCell className="text-muted-foreground">{relativeTime(account.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      <Button size="sm" onClick={() => setApproving(account)}>
                        <UserCheckIcon aria-hidden="true" />
                        Approve
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <PaginationBar page={page} onPageChange={goToPage} />
        </>
      ) : null}

      <ApproveUserDialog
        account={approving}
        open={approving !== null}
        onOpenChange={(open) => {
          if (!open) setApproving(null)
        }}
      />
    </div>
  )
}
