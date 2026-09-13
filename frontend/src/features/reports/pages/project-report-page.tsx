import { FolderKanbanIcon, LockIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import { useActiveWorkspaceSlug } from '@/hooks/use-active-workspace'

import { ProjectProgressTable } from '../components/project-progress-table'
import { ReportFilters } from '../components/report-filters'
import { ReportShell } from '../components/report-shell'
import { SortSelect } from '../components/sort-select'
import {
  DEFAULT_PROJECT_SORT,
  PROJECT_STATUSES,
  PROJECT_SORTS,
  isProjectStatus,
} from '../constants'
import { useProjectReport, useReportPermissions } from '../hooks'
import { readEnumList, readId, readPage, readSort, withParam } from '../url-state'

/**
 * Project completion and progress.
 *
 * The one report behind two codes rather than one: `GET /reports/projects`
 * insists on `project:read` as well as `task:read`, because it shows a
 * project's progress beside counts derived from its tasks, and somebody who
 * could obtain neither half from its own listing should not obtain the pair
 * here. That refusal is explained rather than hidden, since holding `task:read`
 * without `project:read` is unusual enough that a missing screen would puzzle.
 *
 * Status is the only filter that takes several values at once, so it is a row
 * of toggles rather than a dropdown: the endpoint binds a repeated `status` key
 * to a list, and picking two states is an ordinary thing to want.
 */
export function ProjectReportPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const slug = useActiveWorkspaceSlug() ?? ''
  const { canReadProjectReport } = useReportPermissions()

  const statuses = readEnumList(searchParams, 'status', isProjectStatus)
  const filters = {
    status: statuses,
    teamId: readId(searchParams, 'teamId'),
    ownerUserId: readId(searchParams, 'ownerUserId'),
  }
  const page = readPage(searchParams)
  const sort = readSort(searchParams, PROJECT_SORTS, DEFAULT_PROJECT_SORT)

  const report = useProjectReport(filters, page, sort)

  const apply = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  const toggleStatus = (value: string) => {
    const current = statuses ?? []
    const next = current.includes(value)
      ? current.filter((status) => status !== value)
      : [...current, value]
    apply(withParam(searchParams, 'status', next.length === 0 ? undefined : next))
  }

  if (!canReadProjectReport) {
    return (
      <ReportShell title="Project progress">
        <EmptyState
          icon={LockIcon}
          title="You cannot open this report"
          description="Reading a project report needs both project:read and task:read in this workspace. An administrator can grant them."
        />
      </ReportShell>
    )
  }

  const data = report.data

  return (
    <ReportShell title="Project progress" description="Completion and outstanding work by project">
      <div className="space-y-3">
        <ReportFilters params={searchParams} onChange={apply} team owner>
          <SortSelect
            id="project-report-sort"
            value={sort}
            options={PROJECT_SORTS}
            onChange={(next) => apply(withParam(searchParams, 'sort', next))}
          />
        </ReportFilters>

        <div className="flex flex-wrap items-center gap-2">
          {PROJECT_STATUSES.map((status) => {
            const on = (statuses ?? []).includes(status.value)
            return (
              <Button
                key={status.value}
                variant={on ? 'secondary' : 'outline'}
                size="sm"
                aria-pressed={on}
                onClick={() => toggleStatus(status.value)}
              >
                {status.label}
              </Button>
            )
          })}
        </div>
      </div>

      {report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : report.isPending ? (
        <LoadingState label="Loading the project report" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={FolderKanbanIcon}
          title="No projects match"
          description={
            statuses || filters.teamId || filters.ownerUserId
              ? 'Nothing in your reach matches these filters.'
              : 'There are no projects in your reach yet.'
          }
        />
      ) : data ? (
        <div className="space-y-4">
          <ProjectProgressTable projects={data.content} workspaceSlug={slug} />
          <PaginationBar
            page={data}
            onPageChange={(next) => apply(withParam(searchParams, 'page', String(next + 1)))}
          />
        </div>
      ) : null}
    </ReportShell>
  )
}
