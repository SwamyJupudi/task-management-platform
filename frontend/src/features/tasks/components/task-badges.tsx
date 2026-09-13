import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

import { PRIORITY_LABELS, STATUS_LABELS } from '../constants'
import type { TaskPriority, TaskStatus } from '../types'

/**
 * The two chips a task is read by, defined once.
 *
 * Colour carries meaning rather than decoration: the destructive tone is
 * reserved for critical priority, and a finished task reads muted so the eye
 * passes over it. Everything else stays neutral.
 */

const statusTone: Readonly<Record<TaskStatus, string>> = {
  TODO: 'border-border text-foreground',
  IN_PROGRESS: 'border-transparent bg-primary/15 text-foreground',
  REVIEW: 'border-border text-foreground',
  DONE: 'border-transparent bg-muted text-muted-foreground',
}

export function TaskStatusBadge({ status, className }: { status: TaskStatus; className?: string }) {
  return (
    <Badge variant="outline" className={cn(statusTone[status], className)}>
      {STATUS_LABELS[status]}
    </Badge>
  )
}

const priorityTone: Readonly<Record<TaskPriority, string>> = {
  LOW: 'text-muted-foreground',
  MEDIUM: 'text-muted-foreground',
  HIGH: 'text-foreground',
  CRITICAL: 'text-destructive',
}

export function TaskPriorityBadge({
  priority,
  className,
}: {
  priority: TaskPriority
  className?: string
}) {
  return (
    <Badge
      variant={priority === 'CRITICAL' ? 'destructive' : 'outline'}
      className={cn(priority !== 'CRITICAL' && priorityTone[priority], className)}
    >
      {PRIORITY_LABELS[priority]}
    </Badge>
  )
}

/**
 * Marks a task that something unfinished is waiting on.
 *
 * Surfaced, never enforced: the requirements state no rule about starting or
 * finishing blocked work, so this says what is true and stops there. Editing
 * the dependency graph is its own feature.
 */
export function BlockedBadge({ className }: { className?: string }) {
  return (
    <Badge variant="outline" className={cn('border-destructive/40 text-destructive', className)}>
      Blocked
    </Badge>
  )
}
