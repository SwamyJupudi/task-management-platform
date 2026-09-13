import { useState } from 'react'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

import { ALLOWED_TRANSITIONS, STATUS_LABELS, TASK_STATUSES } from '../constants'
import type { Task, TaskStatus } from '../types'
import { BlockedBadge, TaskPriorityBadge } from './task-badges'
import { TaskStatusMenu } from './task-status-menu'

/**
 * The board: one column per status, and a drag between columns is a status
 * change.
 *
 * **There is no within-column reordering.** `boardPosition` exists on the task
 * and is settable, but a gapless drag ordering is a design the platform has not
 * settled, and there is no endpoint that reorders a column. A board that let
 * cards be dropped into a position would be showing an order the server neither
 * computes nor returns — it would survive until the next refetch and then
 * quietly rearrange itself. So cards sit in the order the query returned them,
 * and only the column they are in can be changed.
 *
 * A drag onto a column the state machine forbids is refused before it starts:
 * the column reports that it will not accept the drop, so the pointer says no
 * rather than the server saying it afterwards.
 *
 * Dragging is not the only way to move a card. Native HTML drag-and-drop cannot
 * be driven from a keyboard, so every card also carries the same status menu the
 * detail screen uses — which is the accessible path rather than a fallback, and
 * is also what a caller uses on a touch screen.
 */

function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const second = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return `${first}${second}`.toUpperCase() || '?'
}

function BoardCard({
  task,
  workspaceSlug,
  canMove,
  onStatusChange,
  onDragStart,
  onDragEnd,
  busy,
}: {
  task: Task
  workspaceSlug: string
  canMove: boolean
  onStatusChange: (task: Task, status: TaskStatus) => void
  onDragStart: (task: Task) => void
  onDragEnd: () => void
  busy: boolean
}) {
  return (
    <li
      draggable={canMove}
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move'
        // Some browsers refuse to start a drag without payload; the task itself
        // is tracked in state, since only this page can interpret it anyway.
        event.dataTransfer.setData('text/plain', task.key)
        onDragStart(task)
      }}
      onDragEnd={onDragEnd}
      className={cn(
        'rounded-lg border border-border bg-card p-3',
        canMove && 'cursor-grab active:cursor-grabbing',
        busy && 'opacity-60',
      )}
    >
      <Link
        to={paths.workspace.task(workspaceSlug, task.id)}
        className="block truncate text-sm font-medium hover:underline"
      >
        {task.title}
      </Link>

      <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
        <span className="font-mono">{task.key}</span>
        <span aria-hidden="true">·</span>
        <span className="truncate">{task.projectName}</span>
      </p>

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <TaskPriorityBadge priority={task.priority} />
        {task.blocked ? <BlockedBadge /> : null}
        {task.labels.slice(0, 2).map((label) => (
          <Badge key={label} variant="secondary">
            {label}
          </Badge>
        ))}
      </div>

      <div className="mt-2 flex items-center justify-between gap-2">
        {task.assigneeName ? (
          <span className="flex min-w-0 items-center gap-1.5">
            <Avatar size="sm">
              <AvatarFallback>{initials(task.assigneeName)}</AvatarFallback>
            </Avatar>
            <span className="truncate text-xs text-muted-foreground">{task.assigneeName}</span>
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">Unassigned</span>
        )}

        {canMove ? (
          <TaskStatusMenu
            task={task}
            size="xs"
            disabled={busy}
            onSelect={(status) => onStatusChange(task, status)}
          />
        ) : null}
      </div>
    </li>
  )
}

export function TaskBoard({
  tasks,
  workspaceSlug,
  canMove,
  onStatusChange,
  pendingId,
}: {
  tasks: Task[]
  workspaceSlug: string
  /** Whether the caller may move this particular task. */
  canMove: (task: Task) => boolean
  onStatusChange: (task: Task, status: TaskStatus) => void
  pendingId: string | null
}) {
  const [dragging, setDragging] = useState<Task | null>(null)

  /** A column accepts a drop only when the state machine allows the move. */
  const accepts = (status: TaskStatus) =>
    dragging !== null &&
    dragging.status !== status &&
    ALLOWED_TRANSITIONS[dragging.status].includes(status)

  return (
    <div className="overflow-x-auto pb-2">
      <div className="flex min-w-max gap-3">
        {TASK_STATUSES.map((status) => {
          const column = tasks.filter((task) => task.status === status)
          const droppable = accepts(status)

          return (
            <section
              key={status}
              aria-label={`${STATUS_LABELS[status]}, ${column.length} on this page`}
              onDragOver={(event) => {
                if (!droppable) return
                // Preventing the default is what marks the column as a drop
                // target; leaving it alone is how an illegal move is refused.
                event.preventDefault()
                event.dataTransfer.dropEffect = 'move'
              }}
              onDrop={(event) => {
                if (!droppable || dragging === null) return
                event.preventDefault()
                onStatusChange(dragging, status)
                setDragging(null)
              }}
              className={cn(
                'flex w-72 shrink-0 flex-col rounded-lg p-2 transition-colors',
                droppable ? 'bg-primary/10 ring-2 ring-primary/40' : 'bg-muted/40',
                dragging !== null && !droppable && dragging.status !== status && 'opacity-50',
              )}
            >
              <header className="flex items-center justify-between gap-2 px-1 pb-2">
                <h3 className="text-sm font-medium">{STATUS_LABELS[status]}</h3>
                <Badge variant="outline" className="tabular-nums">
                  {column.length}
                </Badge>
              </header>

              {column.length === 0 ? (
                <p className="rounded-md border border-dashed border-border px-3 py-6 text-center text-xs text-muted-foreground">
                  {droppable ? 'Drop here' : 'Nothing here'}
                </p>
              ) : (
                <ul className="space-y-2">
                  {column.map((task) => (
                    <BoardCard
                      key={task.id}
                      task={task}
                      workspaceSlug={workspaceSlug}
                      canMove={canMove(task)}
                      onStatusChange={onStatusChange}
                      onDragStart={setDragging}
                      onDragEnd={() => setDragging(null)}
                      busy={pendingId === task.id}
                    />
                  ))}
                </ul>
              )}
            </section>
          )
        })}
      </div>
    </div>
  )
}
