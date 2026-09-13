import { CalendarClockIcon, CircleAlertIcon, ListChecksIcon, SquareCheckIcon } from 'lucide-react'

import { DistributionChart } from '@/components/charts/distribution-chart'
import { ErrorState } from '@/components/common/error-state'
import { Panel } from '@/components/common/panel'
import { StatCard } from '@/components/common/stat-card'

import { useEmployeeDashboard } from '../hooks'
import { ProjectProgressList } from './project-progress-list'
import { RecentActivityList } from './recent-activity-list'
import { UpcomingDeadlinesList } from './upcoming-deadlines-list'

/**
 * One person's own work.
 *
 * A single request behind the whole screen, because the backend assembles it in
 * one read-only transaction: the panels are consistent with each other, and a
 * task finished halfway through cannot appear as open in one figure and done in
 * the next. Splitting it into a request per panel here would throw that away.
 *
 * Which means the error state is the whole screen rather than a panel, and is
 * handled once at the top. The panels below it only ever render data or an
 * empty state.
 */
export function EmployeeDashboard() {
  const { data, isPending, isError, error, refetch } = useEmployeeDashboard()

  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} />
  }

  const counts = data?.myTaskCounts
  const done = counts?.byStatus.find((entry) => entry.key === 'DONE')?.count ?? 0
  const openTasks = (counts?.total ?? 0) - done

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="My open tasks"
          value={openTasks}
          icon={ListChecksIcon}
          hint={counts ? `${counts.total.toLocaleString()} assigned in total` : undefined}
          loading={isPending}
        />
        <StatCard
          label="Overdue"
          value={data?.overdueCount ?? 0}
          icon={CircleAlertIcon}
          tone={data && data.overdueCount > 0 ? 'destructive' : 'default'}
          hint="Past the due date and not finished"
          loading={isPending}
        />
        <StatCard
          label="Due this week"
          value={data?.upcomingDeadlines.length ?? 0}
          icon={CalendarClockIcon}
          hint="Inside the workspace lead window"
          loading={isPending}
        />
        <StatCard
          label="Open checklist items"
          value={data?.myOpenSubtasks ?? 0}
          icon={SquareCheckIcon}
          hint="Subtasks assigned to you"
          loading={isPending}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          title="My tasks by status"
          description="Every task assigned to you, by board column"
          isLoading={isPending}
          isEmpty={counts?.total === 0}
          emptyTitle="No tasks assigned to you"
          emptyDescription="Work assigned to you will be counted here."
        >
          {counts ? (
            <DistributionChart data={counts.byStatus} ariaLabel="My tasks by status" />
          ) : null}
        </Panel>

        <Panel
          title="My tasks by priority"
          description="The same tasks, by how urgent they are"
          isLoading={isPending}
          isEmpty={counts?.total === 0}
          emptyTitle="No tasks assigned to you"
        >
          {counts ? (
            <DistributionChart data={counts.byPriority} ariaLabel="My tasks by priority" />
          ) : null}
        </Panel>

        <Panel
          title="Due soon"
          description="Your deadlines, soonest first"
          isLoading={isPending}
          isEmpty={data?.upcomingDeadlines.length === 0}
          emptyTitle="Nothing due soon"
          emptyDescription="You have no tasks with a deadline in the next few days."
        >
          {data ? <UpcomingDeadlinesList deadlines={data.upcomingDeadlines} /> : null}
        </Panel>

        <Panel
          title="My projects"
          description="Most recently worked on first"
          isLoading={isPending}
          isEmpty={data?.myProjects.length === 0}
          emptyTitle="No projects yet"
          emptyDescription="Projects you are a member of will appear here."
        >
          {data ? <ProjectProgressList projects={data.myProjects} /> : null}
        </Panel>

        <Panel
          title="My recent activity"
          description="What you did, newest first"
          className="lg:col-span-2"
          isLoading={isPending}
          isEmpty={data?.recentActivity.length === 0}
          emptyTitle="Nothing recorded yet"
          emptyDescription="Your own actions in this workspace will be listed here."
        >
          {data ? <RecentActivityList entries={data.recentActivity} /> : null}
        </Panel>
      </div>
    </div>
  )
}
