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
import { relativeTime } from '@/lib/datetime'

import { humanise } from '../constants'
import type { PlatformProject } from '../types'

/**
 * Every project in the installation, with the workspace that owns it.
 *
 * The workspace column is the whole reason this listing has a shape of its own:
 * inside a workspace it would be noise on every row, and here it is what makes
 * the table readable at all.
 *
 * The rows do not link anywhere. A project's screen lives under
 * `/w/:workspaceSlug`, and a platform administrator is not necessarily a member
 * of the workspace that owns it — following such a link would land them on a
 * 404 from a guard that reports an unreachable workspace as missing. Showing
 * the name honestly beats a link that mostly fails.
 *
 * `progress` is the derived column, read and never recomputed: one rule for it,
 * in the module that owns it.
 */
export function PlatformProjectTable({ projects }: { projects: PlatformProject[] }) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-24">Key</TableHead>
            <TableHead>Project</TableHead>
            <TableHead>Workspace</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="w-40">Progress</TableHead>
            <TableHead>Updated</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {projects.map((project) => (
            <TableRow key={project.id}>
              <TableCell className="font-mono text-xs">{project.key}</TableCell>
              <TableCell className="max-w-[16rem] truncate font-medium">{project.name}</TableCell>
              <TableCell className="max-w-[12rem] truncate text-muted-foreground">
                {project.workspaceName ?? '—'}
              </TableCell>
              <TableCell>
                <Badge variant="outline">{humanise(project.status)}</Badge>
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
              <TableCell className="text-xs whitespace-nowrap text-muted-foreground">
                <time
                  dateTime={project.updatedAt}
                  title={new Date(project.updatedAt).toLocaleString()}
                >
                  {relativeTime(project.updatedAt)}
                </time>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
