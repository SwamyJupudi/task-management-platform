import { useSearchParams } from 'react-router-dom'

import { DistributionChart } from '@/components/charts/distribution-chart'
import { Panel } from '@/components/common/panel'
import { StatCard } from '@/components/common/stat-card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { ReportFilters } from '../components/report-filters'
import { ReportShell } from '../components/report-shell'
import { TrendChart } from '../components/trend-chart'
import { GRANULARITIES, isGranularity } from '../constants'
import { useTaskDistribution, useTrendReport } from '../hooks'
import type { Granularity, TaskReportFilters } from '../types'
import { readDate, readEnum, readId, withParam } from '../url-state'

/**
 * Task completion and status distribution, and the trend underneath it.
 *
 * Two endpoints on one screen because they answer halves of one question. The
 * breakdown says how the work is spread right now; the trend says whether the
 * pile is growing. Reading either alone invites a confident wrong conclusion.
 *
 * The window means different things to the two, and the panels say so rather
 * than leaving it to be assumed. `GET /reports/tasks/distribution` windows on
 * when a task was *created*, and applies no window at all when neither date is
 * given; `GET /reports/trends` always has a window, because a trend without one
 * is not a trend.
 */

/** The sentinel for "let the backend choose", which is usually the right answer. */
const AUTO = '__auto__'

export function TaskReportPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const filters: TaskReportFilters = {
    projectId: readId(searchParams, 'projectId'),
    teamId: readId(searchParams, 'teamId'),
    assigneeUserId: readId(searchParams, 'assigneeUserId'),
    from: readDate(searchParams, 'from'),
    to: readDate(searchParams, 'to'),
  }
  const granularity = readEnum(searchParams, 'granularity', isGranularity) as
    Granularity | undefined

  const distribution = useTaskDistribution(filters)
  const trend = useTrendReport(filters, granularity)

  const apply = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  const counts = distribution.data
  const total = counts?.total ?? 0
  const done = counts?.byStatus.find((entry) => entry.key === 'DONE')?.count ?? 0
  const windowed = filters.from !== undefined || filters.to !== undefined

  return (
    <ReportShell title="Task report" description="How the work is spread, and how it is moving">
      <ReportFilters params={searchParams} onChange={apply} project team assignee period>
        <div className="flex items-center gap-2">
          <Label htmlFor="trend-granularity" className="shrink-0 text-xs text-muted-foreground">
            Buckets
          </Label>
          <Select
            value={granularity ?? AUTO}
            onValueChange={(value) =>
              apply(withParam(searchParams, 'granularity', value === AUTO ? undefined : value))
            }
          >
            <SelectTrigger id="trend-granularity" className="h-8 w-[9rem]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={AUTO}>Automatic</SelectItem>
              {GRANULARITIES.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </ReportFilters>

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Tasks counted" value={total} loading={distribution.isPending} />
        <StatCard label="Completed" value={done} loading={distribution.isPending} />
        <StatCard
          label="Still open"
          value={total - done}
          loading={distribution.isPending}
          hint={total === 0 ? undefined : `${Math.round((done / total) * 100)}% done`}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          title="By status"
          description={
            windowed ? 'Tasks created inside the window' : 'Every task in the current scope'
          }
          isLoading={distribution.isPending}
          error={distribution.error}
          onRetry={() => void distribution.refetch()}
          isEmpty={total === 0}
          emptyTitle="Nothing to count"
          emptyDescription="No task in your reach matches these filters."
        >
          {counts ? <DistributionChart data={counts.byStatus} ariaLabel="Tasks by status" /> : null}
        </Panel>

        <Panel
          title="By priority"
          description={
            windowed ? 'Tasks created inside the window' : 'Every task in the current scope'
          }
          isLoading={distribution.isPending}
          error={distribution.error}
          onRetry={() => void distribution.refetch()}
          isEmpty={total === 0}
          emptyTitle="Nothing to count"
        >
          {counts ? (
            <DistributionChart data={counts.byPriority} ariaLabel="Tasks by priority" />
          ) : null}
        </Panel>
      </div>

      <Panel
        title="Created and completed"
        description="Counted by when each happened, so the two lines can be compared"
        isLoading={trend.isPending}
        error={trend.error}
        onRetry={() => void trend.refetch()}
        isEmpty={trend.data?.length === 0}
        emptyTitle="Nothing in this period"
      >
        {trend.data ? <TrendChart points={trend.data} granularity={granularity} /> : null}
      </Panel>
    </ReportShell>
  )
}
