import { LockIcon, MailPlusIcon, UsersIcon } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'

import { InvitationList } from '../components/invitation-list'
import { InviteDialog } from '../components/invite-dialog'
import { MemberList } from '../components/member-list'
import { useInvitations, useMembers, usePeoplePermissions } from '../hooks'

/**
 * The workspace directory: who is in it, and who has been asked.
 *
 * Two tabs behind one route, with the tab and the page in the query string so a
 * link to the invitations is one somebody can send.
 *
 * There is no search box. Neither endpoint takes a query or a filter — both are
 * paged and nothing more — and a box that only narrowed the twenty rows in hand
 * would claim to search the workspace while doing something else.
 */

type Tab = 'members' | 'invitations'

export function PeoplePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()
  const permissions = usePeoplePermissions()
  const [inviting, setInviting] = useState(false)

  const tab: Tab = searchParams.get('tab') === 'invitations' ? 'invitations' : 'members'
  const pageIndex = Math.max(Number(searchParams.get('page') ?? '1') - 1, 0)

  const members = useMembers(tab === 'members' ? pageIndex : 0)
  const invitations = useInvitations(tab === 'invitations' ? pageIndex : 0)

  const setParams = (mutate: (params: URLSearchParams) => void) => {
    const next = new URLSearchParams(searchParams)
    mutate(next)
    setSearchParams(next, { replace: true })
  }

  const header = (
    <PageHeader
      title="People"
      description={workspace ? `In ${workspace.workspaceName}` : undefined}
      actions={
        permissions.canInvite ? (
          <Button onClick={() => setInviting(true)}>
            <MailPlusIcon aria-hidden="true" />
            Invite
          </Button>
        ) : undefined
      }
    />
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
  const invitationPage = invitations.data

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
          <TabsTrigger value="invitations">Invitations</TabsTrigger>
        </TabsList>

        <TabsContent value="members" className="space-y-4">
          {members.isError ? (
            <ErrorState error={members.error} onRetry={() => void members.refetch()} />
          ) : members.isPending ? (
            <LoadingState label="Loading the roster" />
          ) : memberPage && memberPage.content.length === 0 ? (
            <EmptyState
              icon={UsersIcon}
              title="Nobody here yet"
              description="People who accept an invitation will appear on this list."
            />
          ) : memberPage ? (
            <>
              <MemberList members={memberPage.content} />
              <PaginationBar
                page={memberPage}
                onPageChange={(next) => setParams((params) => params.set('page', String(next + 1)))}
              />
            </>
          ) : null}
        </TabsContent>

        <TabsContent value="invitations" className="space-y-4">
          {invitations.isError ? (
            <ErrorState error={invitations.error} onRetry={() => void invitations.refetch()} />
          ) : invitations.isPending ? (
            <LoadingState label="Loading invitations" />
          ) : invitationPage && invitationPage.content.length === 0 ? (
            <EmptyState
              icon={MailPlusIcon}
              title="No invitations"
              description={
                permissions.canInvite
                  ? 'Invite somebody by email and they will appear here until they accept.'
                  : 'Invitations sent from this workspace will appear here.'
              }
              action={
                permissions.canInvite ? (
                  <Button onClick={() => setInviting(true)}>
                    <MailPlusIcon aria-hidden="true" />
                    Invite
                  </Button>
                ) : undefined
              }
            />
          ) : invitationPage ? (
            <>
              <InvitationList invitations={invitationPage.content} />
              <PaginationBar
                page={invitationPage}
                onPageChange={(next) => setParams((params) => params.set('page', String(next + 1)))}
              />
            </>
          ) : null}
        </TabsContent>
      </Tabs>

      <InviteDialog open={inviting} onOpenChange={setInviting} />
    </div>
  )
}
