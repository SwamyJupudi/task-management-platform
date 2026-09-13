import { CircleAlertIcon, ListChecksIcon, SquareCheckIcon, TimerIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { DistributionChart } from '@/components/charts/distribution-chart'
import { Panel } from '@/components/common/panel'
import { StatCard } from '@/components/common/stat-card'
import { Button } from '@/components/ui/button'
import { useActiveWorkspaceSlug } from '@/hooks/use-active-workspace'

import { OverdueTable } from '../components/overdue-table'
import { ReportShell } from '../components/report-shell'
import { TrendChart } from '../components/trend-chart'
import { DEFAULT_OVERDUE_SORT, DEFAULT_PERIOD_DAYS } from '../constants'
import { useOverdueReport, useTaskDistribution, useTrendReport } from '../hooks'

/**
 * The headline figures, and the way in to each report.
 *
 * Deliberately unfiltered. This screen answers "how are we doing" for the whole
 * of what the reader can see, and every report behind it is where narrowing
 * happens — a filter bar here would only duplicate the one on the screen the
 * reader is about to open.
 *
 * Three requests, each behind its own panel. They are separate endpoints with
 * separate failure modes, so one going down leaves the rest of the screen
 * standing rather than replacing it with a single red box.
 *
 * Every figure here needs only `task:read`, which the route already checked. An
 * empty screen therefore means an empty scope — somebody who reaches no project
 * is answered zeros rather than refused — so the panels say "nothing yet"
 * rather than anything about permission.
 */
export function ReportsOverviewPage() {
  const slug = useActiveWorkspaceSlug() ?? ''

  // No window: the breakdown is over every task in reach, because "how is the
  // work spread" is a question about the work that exists, not about a month.
  const distribution = useTaskDistribution({})
  const trend = useTrendReport({}, undefined)
  const overdue = useOverdueReport({}, 0, DEFAULT_OVERDUE_SORT)

  const counts = distribution.data
  const done = counts?.byStatus.find((entry) => entry.key === 'DONE')?.count ?? 0
  const total = counts?.total ?? 0

  return (
    <ReportShell title="Reports" description="Everything you can see, at a glance">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Tasks"
          value={total}
          icon={ListChecksIcon}
          loading={distribution.isPending}
          hint="Everything in your reach"
        />
        <StatCard
          label="Completed"
          value={done}
          icon={SquareCheckIcon}
          loading={distribution.isPending}
          hint={total === 0 ? 'No tasks yet' : `${Math.round((done / total) * 100)}% of the total`}
        />
        <StatCard
          label="Still open"
          value={total - done}
          icon={TimerIcon}
          loading={distribution.isPending}
        />
        <StatCard
          label="Overdue"
          value={overdue.data?.totalElements ?? 0}
          icon={CircleAlertIcon}
          tone="destructive"
          loading={overdue.isPending}
          hint="Past their date and unfinished"
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          title="Tasks by status"
          description="Every column, including the ones nothing holds"
          isLoading={distribution.isPending}
          error={distribution.error}
          onRetry={() => void distribution.refetch()}
          isEmpty={total === 0}
          emptyTitle="No tasks yet"
          emptyDescription="Status counts appear once there is work to count."
          action={
            slug === '' ? undefined : (
              <Button variant="ghost" size="sm" asChild>
                <Link to={paths.workspace.reportTasks(slug)}>Open report</Link>
              </Button>
            )
          }
        >
          {counts ? <DistributionChart data={counts.byStatus} ariaLabel="Tasks by status" /> : null}
        </Panel>

        <Panel
          title="Tasks by priority"
          isLoading={distribution.isPending}
          error={distribution.error}
          onRetry={() => void distribution.refetch()}
          isEmpty={total === 0}
          emptyTitle="No tasks yet"
        >
          {counts ? (
            <DistributionChart data={counts.byPriority} ariaLabel="Tasks by priority" />
          ) : null}
        </Panel>
      </div>

      <Panel
        title="Created and completed"
        description={`The last ${DEFAULT_PERIOD_DAYS} days`}
        isLoading={trend.isPending}
        error={trend.error}
        onRetry={() => void trend.refetch()}
        isEmpty={trend.data?.length === 0}
        emptyTitle="Nothing in this period"
        emptyDescription="No tasks were created or completed in the window."
      >
        {trend.data ? <TrendChart points={trend.data} /> : null}
      </Panel>

      <Panel
        title="Longest overdue"
        description="The five furthest past their date"
        isLoading={overdue.isPending}
        error={overdue.error}
        onRetry={() => void overdue.refetch()}
        isEmpty={overdue.data?.content.length === 0}
        emptyTitle="Nothing is late"
        emptyDescription="Every task in your reach is either finished or still inside its date."
        contentClassName="px-0"
        action={
          slug === '' ? undefined : (
            <Button variant="ghost" size="sm" asChild>
              <Link to={paths.workspace.reportOverdue(slug)}>See all</Link>
            </Button>
          )
        }
      >
        {overdue.data ? (
          <OverdueTable tasks={overdue.data.content.slice(0, 5)} workspaceSlug={slug} />
        ) : null}
      </Panel>
    </ReportShell>
  )
}
