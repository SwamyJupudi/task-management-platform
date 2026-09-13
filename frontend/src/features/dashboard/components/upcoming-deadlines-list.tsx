import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

import type { UpcomingDeadline } from '../types'

/**
 * The caller's own tasks due inside the backend's lead window.
 *
 * `daysRemaining` is computed server-side in the workspace's timezone and read
 * rather than derived from `dueDate`. A browser in another timezone subtracting
 * dates itself would get a different answer for the same row, and this is the
 * number somebody plans their week by.
 *
 * Zero means today, which is why it is a separate word rather than "in 0 days":
 * a task due at the end of the day somebody is reading their dashboard is the
 * most useful row on it and should read like it.
 */
function whenDue(daysRemaining: number): string {
  if (daysRemaining <= 0) return 'Today'
  if (daysRemaining === 1) return 'Tomorrow'
  return `In ${daysRemaining} days`
}

export function UpcomingDeadlinesList({ deadlines }: { deadlines: UpcomingDeadline[] }) {
  return (
    <ul className="divide-y divide-border">
      {deadlines.map((task) => (
        <li key={task.taskId} className="flex items-center gap-3 py-2 first:pt-0 last:pb-0">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{task.title}</p>
            <p className="truncate text-xs text-muted-foreground">
              <span className="font-mono">{task.key}</span>
              <span aria-hidden="true"> · </span>
              {task.priority.toLowerCase()}
            </p>
          </div>
          <Badge
            variant={task.daysRemaining <= 0 ? 'default' : 'outline'}
            className={cn('shrink-0 whitespace-nowrap')}
          >
            {whenDue(task.daysRemaining)}
          </Badge>
        </li>
      ))}
    </ul>
  )
}
