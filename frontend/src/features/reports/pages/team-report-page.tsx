import { LockIcon, UsersRoundIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { DistributionChart } from '@/components/charts/distribution-chart'
import { EmptyState } from '@/components/common/empty-state'
import { Panel } from '@/components/common/panel'
import { StatCard } from '@/components/common/stat-card'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { TeamPerformanceTable, useWorkspaceDashboard } from '@/features/dashboard'

import { ReportShell } from '../components/report-shell'
import { WorkloadTable } from '../components/workload-table'
import { useReportPermissions, useTeamDashboard, useTeamOptions } from '../hooks'
import { readId, withParam } from '../url-state'

/**
 * Team performance, and one team in detail.
 *
 * The comparison at the top does not come from the reports module at all: team
 * performance is part of `GET /dashboard/workspace`, which is gated on
 * `project:read_any` together with `member:read` and `team:read`. It is refused
 * rather than narrowed for anybody else, because a workspace-wide comparison
 * computed over one person's projects would be wrong rather than discreet —
 * some teams would be missing and the rest would be understated, with nothing
 * on the screen saying so.
 *
 * Picking a team opens `GET /teams/{id}/dashboard`, which is the one figure in
 * this feature that is *not* narrowed per viewer: it is computed over the
 * team's projects whoever is asking, so two people reading it see the same
 * numbers. The gate is narrower to pay for that — leading the team, or holding
 * `project:read_any` — and anybody else is answered 404, which this screen
 * surfaces as the backend's own message rather than guessing at the reason.
 */

/** The sentinel for "no team chosen": a Select item cannot hold an empty value. */
const NONE = '__none__'

export function TeamReportPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canReadTeamPerformance } = useReportPermissions()

  const teams = useTeamOptions()
  const teamId = readId(searchParams, 'teamId')

  const dashboard = useWorkspaceDashboard()
  const team = useTeamDashboard(teamId)

  const apply = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  if (!canReadTeamPerformance) {
    return (
      <ReportShell title="Team performance">
        <EmptyState
          icon={LockIcon}
          title="You cannot open this report"
          description="Comparing teams across the workspace needs project:read_any, member:read and team:read. An administrator can grant them."
        />
      </ReportShell>
    )
  }

  const rows = dashboard.data?.teamPerformance ?? []
  const detail = team.data

  return (
    <ReportShell title="Team performance" description="Every team, and the work in its projects">
      <Panel
        title="All teams"
        description="Counted over the projects each team runs, not over its members"
        isLoading={dashboard.isPending}
        error={dashboard.error}
        onRetry={() => void dashboard.refetch()}
        isEmpty={rows.length === 0}
        emptyTitle="No teams yet"
        emptyDescription="A team appears here once it exists and has projects to run."
        contentClassName="px-0"
      >
        <TeamPerformanceTable teams={rows} />
      </Panel>

      <Panel
        title="One team in detail"
        description={
          detail
            ? `${detail.memberCount.toLocaleString()} people, ${detail.projectCount.toLocaleString()} projects`
            : 'Choose a team to see its members and how its work is spread'
        }
        isLoading={teamId !== undefined && team.isPending}
        error={team.error}
        onRetry={() => void team.refetch()}
        isEmpty={teamId === undefined}
        emptyTitle="No team chosen"
        emptyDescription="Pick a team above to see who is carrying its work."
        action={
          teams.data ? (
            <div className="flex items-center gap-2">
              <Label htmlFor="team-report-team" className="shrink-0 text-xs text-muted-foreground">
                Team
              </Label>
              <Select
                value={teamId ?? NONE}
                onValueChange={(value) =>
                  apply(withParam(searchParams, 'teamId', value === NONE ? undefined : value))
                }
              >
                <SelectTrigger id="team-report-team" className="h-8 w-[12rem]">
                  <SelectValue placeholder="Choose a team" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>No team</SelectItem>
                  {teams.data.map((option) => (
                    <SelectItem key={option.id} value={option.id}>
                      {option.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : undefined
        }
      >
        {detail ? (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard label="Open tasks" value={detail.openTasks} icon={UsersRoundIcon} />
              <StatCard label="Overdue" value={detail.overdueTasks} tone="destructive" />
              <StatCard label="Completed" value={detail.completedTasks} hint="All time" />
              <StatCard
                label="Average progress"
                value={`${detail.averageProgress}%`}
                hint="Mean across its projects"
              />
            </div>

            <div>
              <Progress
                value={detail.averageProgress}
                aria-label={`${detail.name}: ${detail.averageProgress}% average progress`}
              />
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
              <div className="space-y-2">
                <h3 className="text-sm font-medium">Its tasks by status</h3>
                <DistributionChart
                  data={detail.taskDistribution.byStatus}
                  ariaLabel={`${detail.name} tasks by status`}
                />
              </div>
              <div className="space-y-2">
                <h3 className="text-sm font-medium">Its tasks by priority</h3>
                <DistributionChart
                  data={detail.taskDistribution.byPriority}
                  ariaLabel={`${detail.name} tasks by priority`}
                />
              </div>
            </div>

            <div className="space-y-2">
              <h3 className="text-sm font-medium">Who is carrying it</h3>
              {detail.workload.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Nobody on this team is holding open work.
                </p>
              ) : (
                // Members carrying nothing are included rather than dropped: a
                // person with no work is a fact about a team, not a missing row.
                <WorkloadTable rows={detail.workload} />
              )}
            </div>
          </div>
        ) : null}
      </Panel>
    </ReportShell>
  )
}
