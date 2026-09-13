import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'

import type { Task } from '../types'
import { BlockedBadge, TaskPriorityBadge, TaskStatusBadge } from './task-badges'

/**
 * The list view.
 *
 * A table from `md` and a stack of cards below it. These rows are read one at a
 * time — open the one you want — rather than compared down a column, so on a
 * phone the card is the better shape.
 *
 * Row actions are deliberately absent. Whether somebody may move or reassign a
 * task depends on the task's project as well as the task, and the listing does
 * not carry the project's owner or team lead. Rather than guess per row, the
 * controls live on the detail screen where the project is in hand and the
 * answer is the same one the backend gives.
 */

function formatDate(value: string | null): string {
  if (value === null) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

/** True for an unfinished task whose due date has passed. */
function isOverdue(task: Task): boolean {
  if (task.dueDate === null || task.status === 'DONE') return false
  const due = new Date(`${task.dueDate}T23:59:59`)
  return !Number.isNaN(due.getTime()) && due.getTime() < Date.now()
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const second = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return `${first}${second}`.toUpperCase() || '?'
}

function Assignee({ task }: { task: Task }) {
  if (task.assigneeName === null) {
    return <span className="text-sm text-muted-foreground">Unassigned</span>
  }

  return (
    <span className="flex min-w-0 items-center gap-2">
      <Avatar size="sm">
        <AvatarFallback>{initials(task.assigneeName)}</AvatarFallback>
      </Avatar>
      <span className="truncate text-sm">{task.assigneeName}</span>
    </span>
  )
}

function LabelBadges({
  labels,
  onSelect,
}: {
  labels: string[]
  onSelect?: (label: string) => void
}) {
  if (labels.length === 0) return null

  return (
    <span className="flex flex-wrap gap-1">
      {labels.map((label) =>
        onSelect ? (
          <Badge key={label} variant="secondary" asChild className="cursor-pointer">
            <button type="button" onClick={() => onSelect(label)}>
              {label}
            </button>
          </Badge>
        ) : (
          <Badge key={label} variant="secondary">
            {label}
          </Badge>
        ),
      )}
    </span>
  )
}

export function TaskList({
  tasks,
  workspaceSlug,
  onLabelSelect,
  /** Hidden on a screen already scoped to one project. */
  showProject = true,
}: {
  tasks: Task[]
  workspaceSlug: string
  onLabelSelect?: (label: string) => void
  showProject?: boolean
}) {
  return (
    <>
      {/* Cards below md. */}
      <ul className="space-y-3 md:hidden">
        {tasks.map((task) => (
          <li key={task.id} className="rounded-lg border border-border p-4">
            <div className="flex items-start justify-between gap-2">
              <Link
                to={paths.workspace.task(workspaceSlug, task.id)}
                className={cn(
                  'min-w-0 font-medium hover:underline',
                  task.status === 'DONE' && 'text-muted-foreground',
                )}
              >
                {task.title}
              </Link>
              <TaskStatusBadge status={task.status} className="shrink-0" />
            </div>

            <p className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
              <span className="font-mono">{task.key}</span>
              {showProject ? (
                <>
                  <span aria-hidden="true">·</span>
                  <span className="truncate">{task.projectName}</span>
                </>
              ) : null}
              <span aria-hidden="true">·</span>
              <span className={cn(isOverdue(task) && 'font-medium text-destructive')}>
                {task.dueDate === null ? 'No due date' : formatDate(task.dueDate)}
              </span>
            </p>

            <div className="mt-3 flex flex-wrap items-center gap-2">
              <TaskPriorityBadge priority={task.priority} />
              {task.blocked ? <BlockedBadge /> : null}
              <LabelBadges
                labels={task.labels}
                {...(onLabelSelect ? { onSelect: onLabelSelect } : {})}
              />
            </div>

            <div className="mt-3">
              <Assignee task={task} />
            </div>
          </li>
        ))}
      </ul>

      {/* Table from md. */}
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Task</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Priority</TableHead>
              {showProject ? <TableHead>Project</TableHead> : null}
              <TableHead>Assignee</TableHead>
              <TableHead className="text-right">Due</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tasks.map((task) => (
              <TableRow key={task.id}>
                <TableCell className="max-w-[22rem]">
                  <Link
                    to={paths.workspace.task(workspaceSlug, task.id)}
                    className={cn(
                      'block truncate font-medium hover:underline',
                      task.status === 'DONE' && 'text-muted-foreground',
                    )}
                  >
                    {task.title}
                  </Link>
                  <span className="flex flex-wrap items-center gap-1.5">
                    <span className="font-mono text-xs text-muted-foreground">{task.key}</span>
                    {task.blocked ? <BlockedBadge /> : null}
                    <LabelBadges
                      labels={task.labels}
                      {...(onLabelSelect ? { onSelect: onLabelSelect } : {})}
                    />
                  </span>
                </TableCell>
                <TableCell>
                  <TaskStatusBadge status={task.status} />
                </TableCell>
                <TableCell>
                  <TaskPriorityBadge priority={task.priority} />
                </TableCell>
                {showProject ? (
                  <TableCell className="max-w-[10rem] truncate text-muted-foreground">
                    {task.projectName}
                  </TableCell>
                ) : null}
                <TableCell className="max-w-[12rem]">
                  <Assignee task={task} />
                </TableCell>
                <TableCell
                  className={cn(
                    'text-right whitespace-nowrap',
                    isOverdue(task) ? 'font-medium text-destructive' : 'text-muted-foreground',
                  )}
                >
                  {formatDate(task.dueDate)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </>
  )
}
