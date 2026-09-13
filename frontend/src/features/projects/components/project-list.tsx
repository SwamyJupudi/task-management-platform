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

import type { Project } from '../types'
import { ProjectPriorityBadge, ProjectStatusBadge } from './project-badges'

/**
 * The list view.
 *
 * A table from `md` upwards and a stack of cards below it, rather than one
 * table that scrolls sideways. These rows are read one at a time — open the
 * one you want — rather than compared down a column, so on a phone the card is
 * the better shape; the team performance table on the dashboard is the opposite
 * case and scrolls instead.
 *
 * `progress` is the derived column the backend maintains, rendered rather than
 * computed from anything here.
 */

function formatDate(value: string | null): string {
  if (value === null) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
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
    <div className="flex flex-wrap gap-1">
      {labels.map((label) =>
        onSelect ? (
          // Clicking a label filters by it, which is how somebody discovers
          // what is worth filtering by: there is no endpoint listing the
          // workspace's label catalog to populate a picker from.
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
    </div>
  )
}

export function ProjectList({
  projects,
  workspaceSlug,
  onLabelSelect,
}: {
  projects: Project[]
  workspaceSlug: string
  onLabelSelect?: (label: string) => void
}) {
  return (
    <>
      {/* Cards below md. */}
      <ul className="space-y-3 md:hidden">
        {projects.map((project) => (
          <li key={project.id} className="rounded-lg border border-border p-4">
            <div className="flex items-start justify-between gap-2">
              <Link
                to={paths.workspace.project(workspaceSlug, project.id)}
                className="min-w-0 font-medium hover:underline"
              >
                {project.name}
              </Link>
              <ProjectStatusBadge status={project.status} className="shrink-0" />
            </div>

            <p className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
              <span className="font-mono">{project.key}</span>
              <span aria-hidden="true">·</span>
              <span>{project.teamName ?? 'No team'}</span>
              <span aria-hidden="true">·</span>
              <span>{project.ownerName ?? 'No owner'}</span>
            </p>

            <div className="mt-3 flex items-center gap-2">
              <Progress
                value={project.progress}
                className="flex-1"
                aria-label={`${project.name}: ${project.progress}% complete`}
              />
              <span className="w-9 text-right text-xs text-muted-foreground tabular-nums">
                {project.progress}%
              </span>
            </div>

            <div className="mt-3 flex flex-wrap items-center gap-2">
              <ProjectPriorityBadge priority={project.priority} />
              <LabelBadges
                labels={project.labels}
                {...(onLabelSelect ? { onSelect: onLabelSelect } : {})}
              />
            </div>
          </li>
        ))}
      </ul>

      {/* Table from md. */}
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Project</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Team</TableHead>
              <TableHead>Owner</TableHead>
              <TableHead className="w-36">Progress</TableHead>
              <TableHead className="text-right">Ends</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {projects.map((project) => (
              <TableRow key={project.id}>
                <TableCell className="max-w-[18rem]">
                  <Link
                    to={paths.workspace.project(workspaceSlug, project.id)}
                    className="block truncate font-medium hover:underline"
                  >
                    {project.name}
                  </Link>
                  <span className="font-mono text-xs text-muted-foreground">{project.key}</span>
                  <LabelBadges
                    labels={project.labels}
                    {...(onLabelSelect ? { onSelect: onLabelSelect } : {})}
                  />
                </TableCell>
                <TableCell>
                  <ProjectStatusBadge status={project.status} />
                </TableCell>
                <TableCell>
                  <ProjectPriorityBadge priority={project.priority} />
                </TableCell>
                <TableCell className="max-w-[10rem] truncate text-muted-foreground">
                  {project.teamName ?? '—'}
                </TableCell>
                <TableCell className="max-w-[10rem] truncate text-muted-foreground">
                  {project.ownerName ?? '—'}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Progress
                      value={project.progress}
                      className="flex-1"
                      aria-label={`${project.name}: ${project.progress}% complete`}
                    />
                    <span className="w-9 text-right text-xs text-muted-foreground tabular-nums">
                      {project.progress}%
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right whitespace-nowrap text-muted-foreground">
                  {formatDate(project.endDate)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </>
  )
}
