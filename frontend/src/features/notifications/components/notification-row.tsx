import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { toUserMessage } from '@/lib/api'
import { relativeTime } from '@/lib/datetime'
import { cn } from '@/lib/utils'

import { destinationOf, labelOfType } from '../destination'
import { useNotificationMutations } from '../hooks'
import type { Notification } from '../types'

/**
 * One line of the feed.
 *
 * The whole row is the control when there is somewhere to go, so following a
 * notification is one gesture rather than a hunt for a small link. Following it
 * also marks it read, because having opened the thing it is about is precisely
 * what "read" means — leaving it unread afterwards would make the badge lie.
 *
 * The mark-read call is not awaited before navigating. It is idempotent and its
 * result changes nothing on the screen being left, so waiting for it would only
 * delay the thing the reader actually asked for; a failure surfaces as a toast
 * and the badge corrects itself on its next poll.
 *
 * A row with nowhere to go is rendered as plain text rather than a dead
 * control. That is not an edge case to tidy away: the backend nulls the task
 * key when the reader has lost access to the project, so this is the ordinary
 * appearance of a notification about work that is no longer theirs to see.
 */
export function NotificationRow({
  notification,
  workspaceSlug,
}: {
  notification: Notification
  workspaceSlug: string
}) {
  const navigate = useNavigate()
  const { markRead } = useNotificationMutations()

  const unread = notification.readAt === null
  const destination = destinationOf(notification, workspaceSlug)

  const open = () => {
    if (unread) {
      markRead.mutate(notification.id, {
        onError: (error) => toast.error(toUserMessage(error)),
      })
    }
    if (destination) void navigate(destination)
  }

  const body = (
    <>
      <span
        className={cn(
          'mt-1.5 size-2 shrink-0 rounded-full',
          unread ? 'bg-primary' : 'bg-transparent',
        )}
        aria-hidden="true"
      />

      <span className="min-w-0 flex-1 space-y-1">
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <Badge variant="outline" className="shrink-0">
            {labelOfType(notification.type)}
          </Badge>
          {notification.taskKey ? (
            <span className="font-mono text-xs text-muted-foreground">{notification.taskKey}</span>
          ) : null}
          <time
            dateTime={notification.createdAt}
            title={new Date(notification.createdAt).toLocaleString()}
            className="text-xs text-muted-foreground"
          >
            {relativeTime(notification.createdAt)}
          </time>
        </span>

        <span className={cn('block text-sm', !unread && 'text-muted-foreground')}>
          {notification.message}
        </span>
      </span>
    </>
  )

  return (
    <li className={cn('flex', unread && 'bg-primary/[0.04]')}>
      {destination ? (
        <button
          type="button"
          onClick={open}
          className="flex flex-1 items-start gap-3 px-3 py-3 text-left transition-colors hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        >
          {body}
        </button>
      ) : (
        <span className="flex flex-1 items-start gap-3 px-3 py-3">{body}</span>
      )}

      {unread ? (
        <span className="flex items-center pr-2">
          <Button
            variant="ghost"
            size="sm"
            disabled={markRead.isPending}
            aria-label={`Mark as read: ${notification.message}`}
            onClick={() =>
              markRead.mutate(notification.id, {
                onError: (error) => toast.error(toUserMessage(error)),
              })
            }
          >
            Mark read
          </Button>
        </span>
      ) : null}
    </li>
  )
}
