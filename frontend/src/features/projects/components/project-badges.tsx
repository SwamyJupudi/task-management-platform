import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

import { PRIORITY_LABELS, STATUS_LABELS } from '../constants'
import type { ProjectPriority, ProjectStatus } from '../types'

/**
 * The two chips a project is read by, defined once.
 *
 * Colour carries meaning here rather than decoration: the destructive tone is
 * reserved for critical priority, and the muted tone marks the two states that
 * mean "not being worked on". Everything else stays neutral, so the eye is
 * drawn only to the rows that want attention.
 */

const statusTone: Readonly<Record<ProjectStatus, string>> = {
  PLANNING: 'border-border text-foreground',
  ACTIVE: 'border-transparent bg-primary/15 text-foreground',
  ON_HOLD: 'border-border text-muted-foreground',
  COMPLETED: 'border-transparent bg-muted text-muted-foreground',
  ARCHIVED: 'border-dashed border-border text-muted-foreground',
}

export function ProjectStatusBadge({
  status,
  className,
}: {
  status: ProjectStatus
  className?: string
}) {
  return (
    <Badge variant="outline" className={cn(statusTone[status], className)}>
      {STATUS_LABELS[status]}
    </Badge>
  )
}

const priorityTone: Readonly<Record<ProjectPriority, string>> = {
  LOW: 'text-muted-foreground',
  MEDIUM: 'text-muted-foreground',
  HIGH: 'text-foreground',
  CRITICAL: 'text-destructive',
}

export function ProjectPriorityBadge({
  priority,
  className,
}: {
  priority: ProjectPriority
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
