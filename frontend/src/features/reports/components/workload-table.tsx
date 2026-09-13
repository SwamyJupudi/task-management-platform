import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatMinutes } from '@/lib/datetime'
import { cn } from '@/lib/utils'

import type { Workload } from '../types'

/**
 * One row per person: what they are carrying, and what they finished.
 *
 * The effort columns are summed over open work only, which is the backend's
 * rule and worth knowing when reading them: a load is what is still on the
 * desk, and adding the minutes of everything ever finished would make the
 * number grow forever and stop describing anything.
 *
 * `completedInPeriod` is the one column bounded by the window rather than by a
 * status, so the header says so — a bare "Completed" beside four all-time
 * figures would be read as all-time too.
 *
 * A row whose account has been removed keeps its identifier and loses its name.
 * The work still exists and still has to be counted somewhere, so the row says
 * "Former member" rather than disappearing and quietly changing the totals.
 */
export function WorkloadTable({ rows }: { rows: Workload[] }) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Person</TableHead>
            <TableHead className="text-right">Open</TableHead>
            <TableHead className="text-right">In progress</TableHead>
            <TableHead className="text-right">Overdue</TableHead>
            <TableHead className="text-right">Completed in period</TableHead>
            <TableHead className="text-right">Estimated</TableHead>
            <TableHead className="text-right">Logged</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.userId}>
              <TableCell className="max-w-[16rem]">
                <span className="block truncate font-medium">
                  {row.fullName ?? <span className="text-muted-foreground">Former member</span>}
                </span>
                {row.email ? (
                  <span className="block truncate text-xs text-muted-foreground">{row.email}</span>
                ) : null}
              </TableCell>
              <TableCell className="text-right tabular-nums">{row.open.toLocaleString()}</TableCell>
              <TableCell className="text-right tabular-nums">
                {row.inProgress.toLocaleString()}
              </TableCell>
              <TableCell
                className={cn(
                  'text-right tabular-nums',
                  row.overdue > 0 && 'font-medium text-destructive',
                )}
              >
                {row.overdue.toLocaleString()}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {row.completedInPeriod.toLocaleString()}
              </TableCell>
              <TableCell className="text-right text-muted-foreground tabular-nums">
                {formatMinutes(row.estimatedMinutes)}
              </TableCell>
              <TableCell className="text-right text-muted-foreground tabular-nums">
                {formatMinutes(row.actualMinutes)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
