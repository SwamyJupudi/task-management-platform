import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { cn } from '@/lib/utils'

import { PROJECT_STATUSES, STATUS_LABELS } from '../constants'
import type { Project, ProjectStatus } from '../types'
import { ProjectPriorityBadge } from './project-badges'
import { StatusMenu } from './status-menu'

/**
 * The board view: one column per lifecycle state.
 *
 * **Deliberately not drag-and-drop.** A card moves through the menu on it,
 * which calls the same `POST /projects/{id}/status` the list view does. There
 * is no endpoint that reorders projects and no `position` column behind them,
 * so a board that let cards be dragged into an order would be showing an order
 * the server neither stores nor returns — it would survive until the next
 * refetch and then quietly rearrange itself.
 *
 * Dragging *between* columns would be honest, since that is a status change.
 * It is left out because the affordance is indivisible: a board that accepts a
 * drag sideways but silently refuses one upward is worse than one that asks for
 * the move explicitly, and the state machine forbids most pairs anyway.
 *
 * The board renders the page it is given rather than fetching per column. That
 * keeps one source of truth with the list view and one set of filters over
 * both, at the cost of showing only the projects on the current page — which
 * the column counts say plainly rather than implying a total.
 */

function BoardCard({
  project,
  workspaceSlug,
  canChangeStatus,
  onStatusChange,
  busy,
}: {
  project: Project
  workspaceSlug: string
  canChangeStatus: boolean
  onStatusChange: (project: Project, status: ProjectStatus) => void
  busy: boolean
}) {
  return (
    <li className="rounded-lg border border-border bg-card p-3">
      <Link
        to={paths.workspace.project(workspaceSlug, project.id)}
        className="block truncate text-sm font-medium hover:underline"
      >
        {project.name}
      </Link>

      <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
        <span className="font-mono">{project.key}</span>
        {project.teamName ? (
          <>
            <span aria-hidden="true">·</span>
            <span className="truncate">{project.teamName}</span>
          </>
        ) : null}
      </p>

      <div className="mt-2 flex items-center gap-2">
        <Progress
          value={project.progress}
          className="flex-1"
          aria-label={`${project.name}: ${project.progress}% complete`}
        />
        <span className="w-8 text-right text-xs text-muted-foreground tabular-nums">
          {project.progress}%
        </span>
      </div>

      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
        <ProjectPriorityBadge priority={project.priority} />
        {canChangeStatus ? (
          <StatusMenu
            project={project}
            size="xs"
            disabled={busy}
            onSelect={(status) => onStatusChange(project, status)}
          />
        ) : null}
      </div>
    </li>
  )
}

export function ProjectBoard({
  projects,
  workspaceSlug,
  canChangeStatus,
  onStatusChange,
  pendingId,
}: {
  projects: Project[]
  workspaceSlug: string
  /** Whether the caller may move any of these; per-project checks happen above. */
  canChangeStatus: (project: Project) => boolean
  onStatusChange: (project: Project, status: ProjectStatus) => void
  pendingId: string | null
}) {
  return (
    <div className="overflow-x-auto pb-2">
      <div className="flex min-w-max gap-3">
        {PROJECT_STATUSES.map((status) => {
          const column = projects.filter((project) => project.status === status)

          return (
            <section
              key={status}
              aria-label={`${STATUS_LABELS[status]}, ${column.length} on this page`}
              className="flex w-72 shrink-0 flex-col rounded-lg bg-muted/40 p-2"
            >
              <header className="flex items-center justify-between gap-2 px-1 pb-2">
                <h3 className="text-sm font-medium">{STATUS_LABELS[status]}</h3>
                <Badge variant="outline" className="tabular-nums">
                  {column.length}
                </Badge>
              </header>

              {column.length === 0 ? (
                <p
                  className={cn(
                    'rounded-md border border-dashed border-border text-muted-foreground',
                    'px-3 py-6 text-center text-xs',
                  )}
                >
                  Nothing here
                </p>
              ) : (
                <ul className="space-y-2">
                  {column.map((project) => (
                    <BoardCard
                      key={project.id}
                      project={project}
                      workspaceSlug={workspaceSlug}
                      canChangeStatus={canChangeStatus(project)}
                      onStatusChange={onStatusChange}
                      busy={pendingId === project.id}
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
