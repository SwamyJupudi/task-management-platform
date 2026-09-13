import { CircleCheckIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import { StatCard } from '@/components/common/stat-card'
import { useActiveWorkspaceSlug } from '@/hooks/use-active-workspace'

import { OverdueTable } from '../components/overdue-table'
import { ReportFilters } from '../components/report-filters'
import { ReportShell } from '../components/report-shell'
import { SortSelect } from '../components/sort-select'
import { DEFAULT_OVERDUE_SORT, OVERDUE_SORTS } from '../constants'
import { useOverdueReport } from '../hooks'
import type { OverdueReportFilters } from '../types'
import { readId, readPage, readSort, withParam } from '../url-state'

/**
 * Everything past its date and not finished.
 *
 * No window, and no control offering one, because the endpoint takes none: a
 * task is late as of today in the workspace's timezone, and "overdue during
 * March" is a different question nothing here answers.
 *
 * `daysOverdue` comes from the backend rather than being computed from the due
 * date on this side. A reader in another timezone subtracting dates themselves
 * would get a different answer for the same row, and this is the number people
 * sort and escalate by.
 *
 * An empty result is the good outcome here, so it says so plainly rather than
 * using the usual "no results" wording.
 */
export function OverdueReportPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const slug = useActiveWorkspaceSlug() ?? ''

  const filters: OverdueReportFilters = {
    projectId: readId(searchParams, 'projectId'),
    teamId: readId(searchParams, 'teamId'),
    assigneeUserId: readId(searchParams, 'assigneeUserId'),
  }
  const page = readPage(searchParams)
  const sort = readSort(searchParams, OVERDUE_SORTS, DEFAULT_OVERDUE_SORT)

  const report = useOverdueReport(filters, page, sort)
  const apply = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  const data = report.data
  const filtered =
    filters.projectId !== undefined ||
    filters.teamId !== undefined ||
    filters.assigneeUserId !== undefined

  return (
    <ReportShell title="Overdue tasks" description="Past their due date and still open">
      <ReportFilters params={searchParams} onChange={apply} project team assignee>
        <SortSelect
          id="overdue-report-sort"
          value={sort}
          options={OVERDUE_SORTS}
          onChange={(next) => apply(withParam(searchParams, 'sort', next))}
        />
      </ReportFilters>

      <StatCard
        label="Overdue tasks"
        value={data?.totalElements ?? 0}
        tone="destructive"
        loading={report.isPending}
        hint="Counted as of today in the workspace timezone"
        className="sm:max-w-xs"
      />

      {report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : report.isPending ? (
        <LoadingState label="Loading overdue tasks" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={CircleCheckIcon}
          title={filtered ? 'Nothing late here' : 'Nothing is late'}
          description={
            filtered
              ? 'No task matching these filters is past its date.'
              : 'Every task in your reach is either finished or still inside its date.'
          }
        />
      ) : data ? (
        <div className="space-y-4">
          <OverdueTable tasks={data.content} workspaceSlug={slug} />
          <PaginationBar
            page={data}
            onPageChange={(next) => apply(withParam(searchParams, 'page', String(next + 1)))}
          />
        </div>
      ) : null}
    </ReportShell>
  )
}
