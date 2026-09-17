import { LockIcon, UserCheckIcon, UsersIcon } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { relativeTime } from '@/lib/datetime'

import { ApprovePendingDialog } from '../components/approve-pending-dialog'
import { MemberList } from '../components/member-list'
import { useMembers, usePendingUsers, usePeoplePermissions } from '../hooks'
import type { PendingUser } from '../types'

/**
 * The workspace directory: who is in it, and who is waiting to be.
 *
 * Onboarding is by approval now. Somebody registers, appears in the pending
 * queue, and whoever administers this workspace decides whether they join it and
 * what they may do here. That decision is workspace-scoped — `member:invite` in
 * this workspace, which an ADMIN holds and a TEAM_LEAD or EMPLOYEE does not — so
 * the queue is shown only to somebody who could act on it, and the tab is absent
 * rather than disabled for everybody else.
 *
 * The queue itself is not workspace-scoped and cannot be: a registration belongs
 * to no workspace until it is approved into one.
 *
 * There is no search box. Neither endpoint takes a query or a filter, and a box
 * that only narrowed the twenty rows in hand would claim to search the workspace
 * while doing something else.
 */

type Tab = 'members' | 'pending'

export function PeoplePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()
  const permissions = usePeoplePermissions()
  const [approving, setApproving] = useState<PendingUser | null>(null)

  const tab: Tab = searchParams.get('tab') === 'pending' && permissions.canInvite ? 'pending' : 'members'
  const pageIndex = Math.max(Number(searchParams.get('page') ?? '1') - 1, 0)

  const members = useMembers(tab === 'members' ? pageIndex : 0)
  const pending = usePendingUsers(tab === 'pending' ? pageIndex : 0)

  const setParams = (mutate: (params: URLSearchParams) => void) => {
    const next = new URLSearchParams(searchParams)
    mutate(next)
    setSearchParams(next, { replace: true })
  }

  const header = (
    <PageHeader title="People" description={workspace ? `In ${workspace.workspaceName}` : undefined} />
  )

  // Reading the roster needs member:read. Saying so beats a red panel holding a
  // 403 the user can do nothing about.
  if (!permissions.canReadMembers) {
    return (
      <div className="space-y-6">
        {header}
        <EmptyState
          icon={LockIcon}
          title="You cannot see the roster here"
          description="Your role in this workspace does not include reading its members. An administrator can grant it."
        />
      </div>
    )
  }

  const memberPage = members.data
  const pendingPage = pending.data

  const roster = members.isError ? (
    <ErrorState error={members.error} onRetry={() => void members.refetch()} />
  ) : members.isPending ? (
    <LoadingState label="Loading the roster" />
  ) : memberPage && memberPage.content.length === 0 ? (
    <EmptyState
      icon={UsersIcon}
      title="Nobody here yet"
      description="People appear here once somebody approves them into this workspace."
    />
  ) : memberPage ? (
    <>
      <MemberList members={memberPage.content} />
      <PaginationBar
        page={memberPage}
        onPageChange={(next) => setParams((params) => params.set('page', String(next + 1)))}
      />
    </>
  ) : null

  // Only rendered for somebody holding member:invite, so there is no case here
  // for "you may look but not act".
  if (!permissions.canInvite) {
    return (
      <div className="space-y-6">
        {header}
        {roster}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {header}

      <Tabs
        value={tab}
        onValueChange={(next) =>
          setParams((params) => {
            if (next === 'members') params.delete('tab')
            else params.set('tab', next)
            // The two lists page independently; carrying a page number across
            // would land on a page the other list may not have.
            params.delete('page')
          })
        }
        className="space-y-4"
      >
        <TabsList>
          <TabsTrigger value="members">Members</TabsTrigger>
          <TabsTrigger value="pending">Pending</TabsTrigger>
        </TabsList>

        <TabsContent value="members" className="space-y-4">
          {roster}
        </TabsContent>

        <TabsContent value="pending" className="space-y-4">
          {pending.isError ? (
            <ErrorState error={pending.error} onRetry={() => void pending.refetch()} />
          ) : pending.isPending ? (
            <LoadingState label="Loading the queue" />
          ) : pendingPage && pendingPage.content.length === 0 ? (
            <EmptyState
              icon={UserCheckIcon}
              title="Nobody is waiting"
              description="New registrations appear here until somebody approves them into a workspace."
            />
          ) : pendingPage ? (
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
                    {pendingPage.content.map((account) => (
                      <TableRow key={account.id}>
                        <TableCell className="font-medium">
                          {account.firstName} {account.lastName}
                        </TableCell>
                        <TableCell className="text-muted-foreground">{account.email}</TableCell>
                        <TableCell className="text-muted-foreground">
                          {relativeTime(account.createdAt)}
                        </TableCell>
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
              <PaginationBar
                page={pendingPage}
                onPageChange={(next) => setParams((params) => params.set('page', String(next + 1)))}
              />
            </>
          ) : null}
        </TabsContent>
      </Tabs>

      <ApprovePendingDialog
        account={approving}
        open={approving !== null}
        onOpenChange={(open) => {
          if (!open) setApproving(null)
        }}
      />
    </div>
  )
}
