import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'

import type { ProjectProgress } from '../types'

/**
 * One line per project: how far along it is, and what is behind.
 *
 * `progress` is the derived column the projects module maintains, rendered
 * rather than recomputed from the task counts beside it. The two answer
 * different questions — progress is how much of the work is done, `overdueTasks`
 * is how much of the rest is late — and deriving either from the other here
 * would be a second rule for a number the platform defines once.
 *
 * The status is rendered as the backend spells it, humanised. The report filters
 * by project status but returns no labelled breakdown, so there is no label to
 * read from the response; the filter's own list names them properly and this
 * only has to be readable.
 *
 * A table rather than cards, because these rows exist to be compared down a
 * column. It scrolls sideways below `sm` instead of stacking: a comparison
 * broken into separate blocks is no longer a comparison.
 */

/** `ON_HOLD` reads better as "On hold" and an unknown state still reads. */
function humaniseStatus(status: string): string {
  const words = status.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function ProjectProgressTable({
  projects,
  workspaceSlug,
}: {
  projects: ProjectProgress[]
  /** Empty while the workspace is resolving; the keys then read as plain text. */
  workspaceSlug: string
}) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-24">Key</TableHead>
            <TableHead>Project</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="w-40">Progress</TableHead>
            <TableHead className="text-right">Done</TableHead>
            <TableHead className="text-right">Tasks</TableHead>
            <TableHead className="text-right">Overdue</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {projects.map((project) => (
            <TableRow key={project.projectId}>
              <TableCell className="font-mono text-xs">
                {workspaceSlug === '' ? (
                  project.key
                ) : (
                  <Link
                    to={paths.workspace.project(workspaceSlug, project.projectId)}
                    className="underline-offset-4 hover:underline"
                  >
                    {project.key}
                  </Link>
                )}
              </TableCell>
              <TableCell className="max-w-[16rem] truncate font-medium">{project.name}</TableCell>
              <TableCell>
                <Badge variant="outline">{humaniseStatus(project.status)}</Badge>
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <Progress
                    value={project.progress}
                    className="flex-1"
                    aria-label={`${project.name}: ${project.progress}% complete`}
                  />
                  <span className="w-9 shrink-0 text-right text-xs text-muted-foreground tabular-nums">
                    {project.progress}%
                  </span>
                </div>
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {project.doneTasks.toLocaleString()}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {project.totalTasks.toLocaleString()}
              </TableCell>
              <TableCell
                className={cn(
                  'text-right tabular-nums',
                  project.overdueTasks > 0 && 'font-medium text-destructive',
                )}
              >
                {project.overdueTasks.toLocaleString()}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
