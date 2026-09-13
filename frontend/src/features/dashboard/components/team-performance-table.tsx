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

import type { TeamPerformance } from '../types'

/**
 * One line per team: how much work it is carrying and how far along it is.
 *
 * The counts are over the team's projects rather than over its members, which
 * is the backend's rule and worth knowing when reading the table: a team is
 * responsible for the work in the projects it runs, including work assigned to
 * somebody borrowed from elsewhere.
 *
 * A table rather than cards, because these rows are meant to be compared down a
 * column. It scrolls sideways below `sm` instead of stacking: a comparison that
 * has been broken into separate blocks is no longer a comparison.
 */
export function TeamPerformanceTable({ teams }: { teams: TeamPerformance[] }) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Team</TableHead>
            <TableHead className="text-right">People</TableHead>
            <TableHead className="text-right">Projects</TableHead>
            <TableHead className="text-right">Open</TableHead>
            <TableHead className="text-right">Overdue</TableHead>
            <TableHead className="w-32">Progress</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {teams.map((team) => (
            <TableRow key={team.teamId}>
              <TableCell className="max-w-[12rem] truncate font-medium">{team.name}</TableCell>
              <TableCell className="text-right tabular-nums">
                {team.memberCount.toLocaleString()}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {team.projectCount.toLocaleString()}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {team.openTasks.toLocaleString()}
              </TableCell>
              <TableCell
                className={cn(
                  'text-right tabular-nums',
                  team.overdueTasks > 0 && 'font-medium text-destructive',
                )}
              >
                {team.overdueTasks.toLocaleString()}
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <Progress
                    value={team.averageProgress}
                    className="flex-1"
                    aria-label={`${team.name}: ${team.averageProgress}% average progress`}
                  />
                  <span className="w-9 shrink-0 text-right text-xs text-muted-foreground tabular-nums">
                    {team.averageProgress}%
                  </span>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
