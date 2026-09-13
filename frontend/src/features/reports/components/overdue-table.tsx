import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
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

import type { OverdueTask } from '../types'

/**
 * Every task past its date and not finished.
 *
 * `daysOverdue` is the backend's figure, counted against today in the
 * workspace's timezone. Subtracting the dates here would give a reader in
 * another timezone a different answer for the same row, and this is the number
 * people sort and escalate by.
 *
 * The row links to the task rather than opening a drawer, because the next
 * thing somebody does with a late task is work on it, and the task screen is
 * where that happens.
 *
 * An unassigned row says so rather than leaving the cell blank. Nobody holding
 * a late task is the most actionable line in the table, not a missing value.
 */

/** The backend sends the enum; there is no labelled breakdown to read from. */
function humanise(value: string): string {
  const words = value.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function OverdueTable({
  tasks,
  workspaceSlug,
}: {
  tasks: OverdueTask[]
  workspaceSlug: string
}) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-28">Key</TableHead>
            <TableHead>Task</TableHead>
            <TableHead>Project</TableHead>
            <TableHead>Assignee</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Priority</TableHead>
            <TableHead>Due</TableHead>
            <TableHead className="text-right">Days late</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tasks.map((task) => (
            <TableRow key={task.taskId}>
              <TableCell className="font-mono text-xs">
                {workspaceSlug === '' ? (
                  task.key
                ) : (
                  <Link
                    to={paths.workspace.task(workspaceSlug, task.taskId)}
                    className="underline-offset-4 hover:underline"
                  >
                    {task.key}
                  </Link>
                )}
              </TableCell>
              <TableCell className="max-w-[18rem] truncate font-medium">{task.title}</TableCell>
              <TableCell className="max-w-[10rem] truncate text-muted-foreground">
                {workspaceSlug === '' ? (
                  task.projectName
                ) : (
                  <Link
                    to={paths.workspace.project(workspaceSlug, task.projectId)}
                    className="underline-offset-4 hover:underline"
                  >
                    {task.projectName}
                  </Link>
                )}
              </TableCell>
              <TableCell className="max-w-[10rem] truncate">
                {task.assigneeName ?? <span className="text-muted-foreground">Unassigned</span>}
              </TableCell>
              <TableCell>
                <Badge variant="outline">{humanise(task.status)}</Badge>
              </TableCell>
              <TableCell>
                <Badge variant={task.priority === 'CRITICAL' ? 'destructive' : 'outline'}>
                  {humanise(task.priority)}
                </Badge>
              </TableCell>
              <TableCell className="whitespace-nowrap text-muted-foreground tabular-nums">
                {task.dueDate}
              </TableCell>
              <TableCell
                className={cn(
                  'text-right font-medium tabular-nums',
                  task.daysOverdue > 0 && 'text-destructive',
                )}
              >
                {task.daysOverdue.toLocaleString()}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
