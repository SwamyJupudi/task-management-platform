import { UsersIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Panel } from '@/components/common/panel'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useWorkspaceMembers } from '@/features/people'

import { ReportFilters } from '../components/report-filters'
import { ReportShell } from '../components/report-shell'
import { SortSelect } from '../components/sort-select'
import { TrendChart } from '../components/trend-chart'
import { WorkloadTable } from '../components/workload-table'
import {
  DEFAULT_PERIOD_DAYS,
  DEFAULT_WORKLOAD_SORT,
  GRANULARITIES,
  WORKLOAD_SORTS,
  isGranularity,
} from '../constants'
import { useReportPermissions, useWorkloadReport, useTrendReport } from '../hooks'
import type { Granularity, WorkloadReportFilters } from '../types'
import { readDate, readEnum, readId, readPage, readSort, withParam } from '../url-state'

/**
 * Who is carrying what, and whether they are keeping up.
 *
 * The table is `GET /reports/workload`: one row per person, with the open,
 * in-progress and overdue counts as they stand, and the completed count bounded
 * by the window. That endpoint takes a project, a team and a window, and no
 * assignee — the report is already one row per person, so narrowing it to one
 * person is choosing a row rather than filtering a query.
 *
 * The productivity panel underneath is `GET /reports/trends`, which *does* take
 * an assignee, so one person's throughput can be drawn on its own. Its person
 * picker is deliberately kept out of the filter bar and labelled for the chart:
 * a control that moved the table as well would be promising something the
 * workload endpoint cannot do.
 */

/** "Let the backend choose the buckets", which is usually the right answer. */
const AUTO = '__auto__'

/** "Everybody", for the chart's own picker. */
const EVERYONE = '__everyone__'

export function WorkloadReportPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canReadMembers } = useReportPermissions()
  const members = useWorkspaceMembers()

  const filters: WorkloadReportFilters = {
    projectId: readId(searchParams, 'projectId'),
    teamId: readId(searchParams, 'teamId'),
    from: readDate(searchParams, 'from'),
    to: readDate(searchParams, 'to'),
  }
  const page = readPage(searchParams)
  const sort = readSort(searchParams, WORKLOAD_SORTS, DEFAULT_WORKLOAD_SORT)

  // A separate key from the table's filters, because it narrows the chart only.
  const personId = readId(searchParams, 'personId')
  const granularity = readEnum(searchParams, 'granularity', isGranularity) as
    Granularity | undefined

  const report = useWorkloadReport(filters, page, sort)
  const trend = useTrendReport({ ...filters, assigneeUserId: personId }, granularity)

  const apply = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  const data = report.data
  const person = members.data?.find((member) => member.userId === personId)

  return (
    <ReportShell
      title="Workload"
      description={`Open work now, and what was completed in the period — ${DEFAULT_PERIOD_DAYS} days unless you say otherwise`}
    >
      <ReportFilters params={searchParams} onChange={apply} project team period>
        <SortSelect
          id="workload-report-sort"
          value={sort}
          options={WORKLOAD_SORTS}
          onChange={(next) => apply(withParam(searchParams, 'sort', next))}
        />
      </ReportFilters>

      {report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : report.isPending ? (
        <LoadingState label="Loading the workload report" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={UsersIcon}
          title="Nobody is carrying anything here"
          description="No open work in your reach matches these filters, so there is nothing to attribute."
        />
      ) : data ? (
        <div className="space-y-4">
          <WorkloadTable rows={data.content} />
          <PaginationBar
            page={data}
            onPageChange={(next) => apply(withParam(searchParams, 'page', String(next + 1)))}
          />
        </div>
      ) : null}

      <Panel
        title="Productivity"
        description={
          person
            ? `What ${person.firstName} ${person.lastName} took on and finished`
            : 'What was taken on and finished across the current scope'
        }
        isLoading={trend.isPending}
        error={trend.error}
        onRetry={() => void trend.refetch()}
        isEmpty={trend.data?.length === 0}
        emptyTitle="Nothing in this period"
        action={
          <div className="flex flex-wrap items-center gap-2">
            {canReadMembers && members.data ? (
              <div className="flex items-center gap-2">
                <Label htmlFor="workload-person" className="shrink-0 text-xs text-muted-foreground">
                  Person
                </Label>
                <Select
                  value={personId ?? EVERYONE}
                  onValueChange={(value) =>
                    apply(
                      withParam(searchParams, 'personId', value === EVERYONE ? undefined : value),
                    )
                  }
                >
                  <SelectTrigger id="workload-person" className="h-8 w-[12rem]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={EVERYONE}>Everybody</SelectItem>
                    {members.data.map((member) => (
                      <SelectItem key={member.userId} value={member.userId}>
                        {member.firstName} {member.lastName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}

            <Select
              value={granularity ?? AUTO}
              onValueChange={(value) =>
                apply(withParam(searchParams, 'granularity', value === AUTO ? undefined : value))
              }
            >
              <SelectTrigger className="h-8 w-[9rem]" aria-label="Trend buckets">
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
        }
      >
        {trend.data ? <TrendChart points={trend.data} granularity={granularity} /> : null}
      </Panel>
    </ReportShell>
  )
}
