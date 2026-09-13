import { BellIcon, CheckCheckIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'

import { NotificationRow } from '../components/notification-row'
import { useNotificationFeed, useNotificationMutations, useUnreadNotificationCount } from '../hooks'

/**
 * Everything this person has been told in the active workspace.
 *
 * No permission gate. A notification has exactly one audience, so the backend
 * names the recipient rather than a permission code, and every row on this
 * screen is the reader's own — there is no path, parameter or role that renders
 * somebody else's feed. That is why this page, unlike every other in the
 * application, has no permission-denied state to show.
 *
 * The filter lives in the query string so "my unread notifications" is a link,
 * and the two views are separate queries rather than one filtered in the
 * browser: the endpoint takes `unread` and the counts must be the server's.
 */
export function NotificationsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()

  const unreadOnly = searchParams.get('filter') === 'unread'
  const feed = useNotificationFeed(unreadOnly)
  const { data: unread } = useUnreadNotificationCount()
  const { markAllRead } = useNotificationMutations()

  const rows = feed.data?.pages.flatMap((page) => page.content) ?? []
  const slug = workspace?.workspaceSlug ?? ''
  const unreadCount = unread ?? 0

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notifications"
        description={
          workspace
            ? unreadCount > 0
              ? `${unreadCount.toLocaleString()} unread in ${workspace.workspaceName}`
              : `You are up to date in ${workspace.workspaceName}`
            : undefined
        }
        actions={
          unreadCount > 0 ? (
            <Button
              variant="outline"
              size="sm"
              disabled={markAllRead.isPending}
              onClick={async () => {
                try {
                  const result = await markAllRead.mutateAsync()
                  // The server reports how many it moved, which is the only
                  // honest thing to say: another tab may have read some first.
                  toast.success(
                    result.marked === 0
                      ? 'Everything was already read.'
                      : `Marked ${result.marked.toLocaleString()} as read.`,
                  )
                } catch (error) {
                  toast.error(toUserMessage(error))
                }
              }}
            >
              <CheckCheckIcon aria-hidden="true" />
              {markAllRead.isPending ? 'Marking…' : 'Mark all read'}
            </Button>
          ) : undefined
        }
      />

      <Tabs
        value={unreadOnly ? 'unread' : 'all'}
        onValueChange={(next) => {
          const params = new URLSearchParams(searchParams)
          if (next === 'all') params.delete('filter')
          else params.set('filter', 'unread')
          setSearchParams(params, { replace: true })
        }}
      >
        <TabsList>
          <TabsTrigger value="all">All</TabsTrigger>
          <TabsTrigger value="unread">
            Unread
            {unreadCount > 0 ? (
              <Badge variant="outline" className="ml-1.5 tabular-nums">
                {unreadCount}
              </Badge>
            ) : null}
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {feed.isError ? (
        <ErrorState error={feed.error} onRetry={() => void feed.refetch()} />
      ) : feed.isPending ? (
        <LoadingState label="Loading notifications" />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={BellIcon}
          title={unreadOnly ? 'Nothing unread' : 'No notifications yet'}
          description={
            unreadOnly
              ? 'You have read everything in this workspace.'
              : 'You will be told when work is assigned to you, when somebody mentions you, and when a deadline is close.'
          }
        />
      ) : (
        <div className="space-y-3">
          <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
            {rows.map((notification) => (
              <NotificationRow
                key={notification.id}
                notification={notification}
                workspaceSlug={slug}
              />
            ))}
          </ul>

          {feed.hasNextPage ? (
            <Button
              variant="outline"
              size="sm"
              disabled={feed.isFetchingNextPage}
              onClick={() => void feed.fetchNextPage()}
            >
              {feed.isFetchingNextPage ? 'Loading…' : 'Show older'}
            </Button>
          ) : null}
        </div>
      )}
    </div>
  )
}
