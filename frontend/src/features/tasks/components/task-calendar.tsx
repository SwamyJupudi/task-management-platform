import { useMemo } from 'react'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { cn } from '@/lib/utils'

import { PRIORITY_LABELS, STATUS_LABELS } from '../constants'
import { dayLabel, monthGrid, today, weekdayLabels, type CalendarDay } from '../month'
import type { Task } from '../types'

/**
 * Tasks by deadline, one month at a time.
 *
 * **Due date only.** A task also has a start date, but no endpoint filters on
 * it, so a bar drawn from start to due would be assembled from whatever rows
 * the due-date query happened to return — every task whose span crosses the
 * month but whose deadline falls outside it would be missing, and the picture
 * would be confidently wrong. One date, one cell.
 *
 * Tasks without a deadline never appear here, and that is the query's doing
 * rather than a choice made in this component: `dueAfter` and `dueBefore` are
 * comparisons a null satisfies neither of. The page says how many were left out.
 *
 * Dates are bucketed and compared as `YYYY-MM-DD` strings. See `month.ts` for
 * why turning a `LocalDate` into a `Date` is how a calendar ends up a day out.
 */

/**
 * How many chips a cell shows before it stops.
 *
 * Three is what fits at the width a seven-column grid leaves on a laptop. Past
 * it the cell says how many more there are and the day itself is the way to see
 * them, which is what makes the day link worth having.
 */
const CHIPS_PER_DAY = 3

/** The chips carry colour; this is what they mean. */
const priorityTone: Readonly<Record<string, string>> = {
  LOW: 'border-l-muted-foreground/40',
  MEDIUM: 'border-l-muted-foreground/40',
  HIGH: 'border-l-foreground',
  CRITICAL: 'border-l-destructive',
}

function TaskChip({
  task,
  workspaceSlug,
  overdue,
}: {
  task: Task
  workspaceSlug: string
  overdue: boolean
}) {
  const label = `${task.key} ${task.title} — ${STATUS_LABELS[task.status]}, ${
    PRIORITY_LABELS[task.priority]
  } priority${overdue ? ', overdue' : ''}`

  const chip = (
    <span
      className={cn(
        'block truncate rounded-sm border-l-2 bg-muted/50 px-1.5 py-1 text-left text-xs',
        priorityTone[task.priority] ?? 'border-l-muted-foreground/40',
        // A finished task is muted whatever its date: it cannot be late.
        task.status === 'DONE' && 'text-muted-foreground line-through',
        overdue && task.status !== 'DONE' && 'bg-destructive/10',
      )}
      title={label}
    >
      <span className="font-mono text-[0.65rem] text-muted-foreground">{task.key}</span>{' '}
      <span className={cn(overdue && task.status !== 'DONE' && 'text-destructive')}>
        {task.title}
      </span>
    </span>
  )

  if (workspaceSlug === '') return chip

  return (
    <Link
      to={paths.workspace.task(workspaceSlug, task.id)}
      aria-label={label}
      className="block rounded-sm focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
    >
      {chip}
    </Link>
  )
}

function DayCell({
  day,
  tasks,
  workspaceSlug,
  now,
  onSelectDay,
}: {
  day: CalendarDay
  tasks: Task[]
  workspaceSlug: string
  now: string
  onSelectDay: (date: string) => void
}) {
  const shown = tasks.slice(0, CHIPS_PER_DAY)
  const hidden = tasks.length - shown.length

  return (
    <div
      className={cn(
        'flex min-h-24 flex-col gap-1 border-t border-l border-border p-1 sm:min-h-28',
        !day.inMonth && 'bg-muted/30',
      )}
    >
      <div className="flex items-baseline justify-between gap-1">
        {/* The day number narrows the whole screen to that date. A button rather
            than a link: it rewrites the query string of the page already open,
            which is what every other filter on this screen does. */}
        <button
          type="button"
          onClick={() => onSelectDay(day.date)}
          aria-label={`Show tasks due on ${dayLabel(day.date)}`}
          className={cn(
            'rounded px-1 text-xs tabular-nums transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
            day.inMonth ? 'text-foreground' : 'text-muted-foreground/60',
            day.isToday && 'bg-primary font-semibold text-primary-foreground hover:bg-primary/90',
          )}
        >
          {day.day}
        </button>

        {tasks.length > 0 ? (
          <span className="text-[0.65rem] text-muted-foreground tabular-nums">{tasks.length}</span>
        ) : null}
      </div>

      <ul className="space-y-1">
        {shown.map((task) => (
          <li key={task.id}>
            <TaskChip
              task={task}
              workspaceSlug={workspaceSlug}
              overdue={day.date < now && task.status !== 'DONE'}
            />
          </li>
        ))}
      </ul>

      {hidden > 0 ? (
        <button
          type="button"
          onClick={() => onSelectDay(day.date)}
          className="rounded px-1 text-left text-xs text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        >
          +{hidden} more
        </button>
      ) : null}
    </div>
  )
}

export function TaskCalendar({
  month,
  tasks,
  workspaceSlug,
  onSelectDay,
}: {
  /** `YYYY-MM`. The month the grid draws and the query asked for. */
  month: string
  /** Every task the month's query returned. Bucketed here rather than upstream. */
  tasks: Task[]
  workspaceSlug: string
  onSelectDay: (date: string) => void
}) {
  const days = useMemo(() => monthGrid(month), [month])
  const weekdays = useMemo(() => weekdayLabels(), [])
  const now = today()

  const byDate = useMemo(() => {
    const buckets = new Map<string, Task[]>()
    for (const task of tasks) {
      if (task.dueDate === null) continue
      const bucket = buckets.get(task.dueDate)
      if (bucket) bucket.push(task)
      else buckets.set(task.dueDate, [task])
    }
    return buckets
  }, [tasks])

  return (
    <div className="overflow-x-auto">
      {/* A month does not usefully reflow below seven columns, so the grid keeps
          its shape and the container scrolls sideways on a phone. */}
      <div className="min-w-[44rem]">
        <div className="grid grid-cols-7 border-r border-b border-border">
          {weekdays.map((weekday) => (
            <div
              key={weekday}
              className="border-l border-border bg-muted/40 px-2 py-1.5 text-xs font-medium text-muted-foreground"
            >
              {weekday}
            </div>
          ))}

          {days.map((day) => (
            <DayCell
              key={day.date}
              day={day}
              tasks={byDate.get(day.date) ?? []}
              workspaceSlug={workspaceSlug}
              now={now}
              onSelectDay={onSelectDay}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
