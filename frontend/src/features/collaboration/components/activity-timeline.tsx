import { HistoryIcon } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { relativeTime } from '@/lib/datetime'

import { useTaskActivity } from '../hooks'

/**
 * What happened to this task, newest first.
 *
 * Includes the history of its subtasks, its comments and its files, which is
 * what makes one timeline beside a task worth reading rather than three.
 *
 * `summary` is composed by the backend when the row is read, from the
 * identifiers on it and the names those resolve to now. It is rendered as it
 * arrives: building the sentence here would be a second copy of a rule the
 * platform already owns, and one that goes stale the first time somebody is
 * renamed.
 *
 * Reading this needs nothing beyond being able to see the task. Browsing the
 * whole workspace's audit trail is an administrator's permission and a
 * different endpoint, which this deliberately does not reach for.
 */
export function ActivityTimeline({ taskId }: { taskId: string }) {
  const activity = useTaskActivity(taskId)

  if (activity.isError) {
    return <ErrorState error={activity.error} onRetry={() => void activity.refetch()} />
  }

  if (activity.isPending) {
    return (
      <div className="space-y-2" role="status" aria-live="polite">
        <span className="sr-only">Loading the history</span>
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-4/5" />
        <Skeleton className="h-6 w-2/3" />
      </div>
    )
  }

  const entries = activity.data.pages.flatMap((page) => page.content)

  if (entries.length === 0) {
    return (
      <EmptyState
        icon={HistoryIcon}
        title="Nothing recorded yet"
        description="Changes to this task, its checklist, its comments and its files will be listed here."
        className="border-0 px-0 py-6"
      />
    )
  }

  return (
    <div className="space-y-3">
      <ol className="divide-y divide-border">
        {entries.map((entry) => (
          <li key={entry.id} className="flex items-baseline gap-3 py-2">
            <p className="min-w-0 flex-1 text-sm">{entry.summary}</p>
            <time
              dateTime={entry.createdAt}
              title={new Date(entry.createdAt).toLocaleString()}
              className="shrink-0 text-xs whitespace-nowrap text-muted-foreground"
            >
              {relativeTime(entry.createdAt)}
            </time>
          </li>
        ))}
      </ol>

      {activity.hasNextPage ? (
        <Button
          variant="outline"
          size="sm"
          disabled={activity.isFetchingNextPage}
          onClick={() => void activity.fetchNextPage()}
        >
          {activity.isFetchingNextPage ? 'Loading…' : 'Show older'}
        </Button>
      ) : null}
    </div>
  )
}
