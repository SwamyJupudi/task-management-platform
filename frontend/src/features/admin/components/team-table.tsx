import type { Team } from '@/features/teams'

import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { relativeTime } from '@/lib/datetime'

/**
 * The teams of one workspace.
 *
 * One workspace at a time, and that is a limit of the API rather than a choice:
 * there is no cross-workspace team listing. `GET /admin/statistics` counts teams
 * installation-wide and `GET /workspaces/{id}/teams` lists them one workspace at
 * a time, so this screen shows the count on the overview and the rows here.
 * Fetching every workspace's teams to concatenate them would be one request per
 * workspace and a listing that could not be paged honestly.
 *
 * A team between leads shows nothing rather than a placeholder name. It is an
 * ordinary state — the team still exists and still has members — and inventing
 * a label for it would make it look like a fault.
 */
export function TeamTable({ teams }: { teams: Team[] }) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Team</TableHead>
            <TableHead>Lead</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Members</TableHead>
            <TableHead>Updated</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {teams.map((team) => (
            <TableRow key={team.id}>
              <TableCell className="max-w-[18rem]">
                <span className="block truncate font-medium">{team.name}</span>
                {team.description ? (
                  <span className="block truncate text-xs text-muted-foreground">
                    {team.description}
                  </span>
                ) : null}
              </TableCell>
              <TableCell className="max-w-[14rem] truncate">
                {team.leadName ?? <span className="text-muted-foreground">No lead</span>}
              </TableCell>
              <TableCell>
                <Badge variant="outline">
                  {team.status === 'ARCHIVED' ? 'Archived' : 'Active'}
                </Badge>
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {team.memberCount.toLocaleString()}
              </TableCell>
              <TableCell className="text-xs whitespace-nowrap text-muted-foreground">
                <time dateTime={team.updatedAt} title={new Date(team.updatedAt).toLocaleString()}>
                  {relativeTime(team.updatedAt)}
                </time>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
