import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'

import type { ProjectProgress } from '../types'

/**
 * Projects with how far along each one is.
 *
 * `progress` is the derived column the projects module maintains, rendered
 * rather than recomputed from the task counts beside it. They answer different
 * questions — progress is how much of the work is done, `overdueTasks` is how
 * much of the rest is late — and deriving one from the other here would create
 * a second rule for a number the platform already defines once.
 *
 * No links yet. The projects feature owns the screen these would point at, and
 * a link to a route that does not exist is worse than a name.
 */
export function ProjectProgressList({ projects }: { projects: ProjectProgress[] }) {
  return (
    <ul className="space-y-4">
      {projects.map((project) => (
        <li key={project.projectId} className="space-y-1.5">
          <div className="flex items-baseline justify-between gap-2">
            <span className="min-w-0 truncate text-sm font-medium">{project.name}</span>
            <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
              {project.progress}%
            </span>
          </div>

          <Progress
            value={project.progress}
            aria-label={`${project.name}: ${project.progress}% complete`}
          />

          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
            <span className="font-mono">{project.key}</span>
            <span aria-hidden="true">·</span>
            <span>
              {project.doneTasks.toLocaleString()} of {project.totalTasks.toLocaleString()} tasks
              done
            </span>
            {project.overdueTasks > 0 ? (
              <Badge variant="destructive">{project.overdueTasks.toLocaleString()} overdue</Badge>
            ) : null}
          </div>
        </li>
      ))}
    </ul>
  )
}
