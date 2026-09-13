import {
  CircleAlertIcon,
  FolderKanbanIcon,
  ListChecksIcon,
  UsersIcon,
  UsersRoundIcon,
} from 'lucide-react'

import { DistributionChart } from '@/components/charts/distribution-chart'
import { ErrorState } from '@/components/common/error-state'
import { Panel } from '@/components/common/panel'
import { StatCard } from '@/components/common/stat-card'
import { Progress } from '@/components/ui/progress'

import { useProjectStatusDistribution, useWorkspaceDashboard } from '../hooks'
import { ProjectProgressList } from './project-progress-list'
import { ProjectStatusChart } from './project-status-chart'
import { TeamPerformanceTable } from './team-performance-table'

/**
 * The whole workspace, for somebody entitled to see the whole workspace.
 *
 * Two queries, not one, and deliberately. The dashboard itself is a single
 * consistent snapshot from one endpoint; the project status breakdown comes
 * from the project report, which is a different endpoint behind a different
 * permission. Keeping them apart is what lets the breakdown fail on its own —
 * a caller holding the dashboard's three codes but not `project:read` sees
 * every other panel and an error in that one.
 *
 * Nothing here is narrowed per viewer. The endpoint refuses a caller without
 * `project:read_any` rather than showing them a smaller version, because a
 * workspace-wide figure computed over one person's projects would be a wrong
 * number rather than a discreet one.
 */
export function WorkspaceDashboard() {
  const { data, isPending, isError, error, refetch } = useWorkspaceDashboard()
  const projectStatus = useProjectStatusDistribution()

  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} />
  }

  const distribution = data?.taskDistribution
  const doneTasks = distribution?.byStatus.find((entry) => entry.key === 'DONE')?.count ?? 0
  const totalTasks = distribution?.total ?? 0

  // Completed work over all work. Derived from the distribution rather than
  // averaged across the project list, which is capped: a mean of the ten most
  // recently touched projects is not the workspace's progress.
  const overallProgress = totalTasks === 0 ? 0 : Math.round((doneTasks / totalTasks) * 100)

  const totalProjects = projectStatus.data?.reduce((sum, entry) => sum + entry.count, 0)

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
        <StatCard
          label="Projects"
          value={totalProjects ?? '—'}
          icon={FolderKanbanIcon}
          hint={data ? `${data.activeProjects.toLocaleString()} active` : undefined}
          loading={projectStatus.isPending}
        />
        <StatCard
          label="Completed projects"
          value={data?.completedProjects ?? 0}
          icon={FolderKanbanIcon}
          loading={isPending}
        />
        <StatCard
          label="Open tasks"
          value={data?.openTasks ?? 0}
          icon={ListChecksIcon}
          hint={distribution ? `${distribution.total.toLocaleString()} in total` : undefined}
          loading={isPending}
        />
        <StatCard
          label="Overdue tasks"
          value={data?.overdueTasks ?? 0}
          icon={CircleAlertIcon}
          tone={data && data.overdueTasks > 0 ? 'destructive' : 'default'}
          loading={isPending}
        />
        <StatCard
          label="Members"
          value={data?.totalMembers ?? 0}
          icon={UsersIcon}
          loading={isPending}
        />
        <StatCard
          label="Teams"
          value={data?.totalTeams ?? 0}
          icon={UsersRoundIcon}
          loading={isPending}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          title="Overall progress"
          description="Tasks finished across the whole workspace"
          isLoading={isPending}
          isEmpty={totalTasks === 0}
          emptyTitle="No tasks yet"
          emptyDescription="Progress is measured over the workspace's tasks."
        >
          <div className="space-y-3">
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-semibold tabular-nums">{overallProgress}%</span>
              <span className="text-sm text-muted-foreground">complete</span>
            </div>
            <Progress
              value={overallProgress}
              aria-label={`Workspace progress: ${overallProgress}% of tasks complete`}
            />
            <p className="text-xs text-muted-foreground">
              {doneTasks.toLocaleString()} of {totalTasks.toLocaleString()} tasks done
            </p>
          </div>
        </Panel>

        <Panel
          title="Projects by status"
          description="Every project in the workspace, by lifecycle state"
          isLoading={projectStatus.isPending}
          error={projectStatus.isError ? projectStatus.error : undefined}
          onRetry={() => void projectStatus.refetch()}
          isEmpty={totalProjects === 0}
          emptyTitle="No projects yet"
          emptyDescription="Projects will be counted here as they are created."
        >
          {projectStatus.data ? <ProjectStatusChart data={projectStatus.data} /> : null}
        </Panel>

        <Panel
          title="Tasks by status"
          description="Every task in the workspace, by board column"
          isLoading={isPending}
          isEmpty={totalTasks === 0}
          emptyTitle="No tasks yet"
        >
          {distribution ? (
            <DistributionChart data={distribution.byStatus} ariaLabel="Tasks by status" />
          ) : null}
        </Panel>

        <Panel
          title="Tasks by priority"
          description="The same tasks, by how urgent they are"
          isLoading={isPending}
          isEmpty={totalTasks === 0}
          emptyTitle="No tasks yet"
        >
          {distribution ? (
            <DistributionChart data={distribution.byPriority} ariaLabel="Tasks by priority" />
          ) : null}
        </Panel>
      </div>

      <Panel
        title="Team performance"
        description="Counted over the projects each team runs, not over its members"
        isLoading={isPending}
        isEmpty={data?.teamPerformance.length === 0}
        emptyTitle="No teams yet"
        emptyDescription="Teams and the work they carry will be listed here."
      >
        {data ? <TeamPerformanceTable teams={data.teamPerformance} /> : null}
      </Panel>

      <Panel
        title="Project progress"
        description="The most recently worked on projects"
        isLoading={isPending}
        isEmpty={data?.projectProgress.length === 0}
        emptyTitle="No projects yet"
        emptyDescription="Projects will appear here as work starts on them."
      >
        {data ? <ProjectProgressList projects={data.projectProgress} /> : null}
      </Panel>
    </div>
  )
}
