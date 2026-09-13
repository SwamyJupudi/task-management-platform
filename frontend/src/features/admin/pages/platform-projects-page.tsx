import { FolderKanbanIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { AdminShell } from '../components/admin-shell'
import { PlatformProjectTable } from '../components/platform-project-table'
import { WorkspacePicker } from '../components/workspace-picker'
import {
  DEFAULT_PROJECT_SORT,
  PROJECT_SORTS,
  PROJECT_STATUSES,
  isProjectStatus,
} from '../constants'
import { useAdminPermissions, usePlatformProjects, useWorkspaceOptions } from '../hooks'
import type { PlatformProjectFilters } from '../types'

/**
 * Every project in the installation.
 *
 * The one listing in the interface with no tenant predicate in front of it,
 * which is why it is paged and why its page size is capped server-side. The
 * workspace control here is a *filter* over an already-authorized read, never a
 * scope: the permission that opened this screen covers all of them, and
 * choosing one narrows what is displayed rather than what is allowed.
 *
 * The owner and team filters the endpoint accepts are not offered. Both take an
 * identifier, and there is no installation-wide listing of people or of teams to
 * populate a picker from — `GET /admin/accounts` pages through accounts but a
 * picker over every account in an installation is not a usable control, and
 * teams can only be listed one workspace at a time. A filter nobody can fill in
 * is worse than one that is absent.
 */

/** A Select item cannot hold an empty value. */
const ANY = '__any__'

export function PlatformProjectsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canReadSystem } = useAdminPermissions()

  const workspaces = useWorkspaceOptions()

  const statusParam = searchParams.get('status')
  const filters: PlatformProjectFilters = {
    workspaceId: searchParams.get('workspaceId') ?? undefined,
    status: statusParam !== null && isProjectStatus(statusParam) ? statusParam : undefined,
  }

  const rawPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) - 1 : 0

  const sortParam = searchParams.get('sort')
  const sort =
    sortParam !== null && PROJECT_SORTS.some((option) => option.value === sortParam)
      ? sortParam
      : DEFAULT_PROJECT_SORT

  const projects = usePlatformProjects(filters, page, sort)

  const setParam = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(searchParams)
    if (value === undefined) next.delete(key)
    else next.set(key, value)
    if (key !== 'page') next.delete('page')
    setSearchParams(next, { replace: true })
  }

  if (!canReadSystem) {
    return (
      <AdminShell title="Projects">
        <EmptyState
          icon={FolderKanbanIcon}
          title="You cannot see every project"
          description="The cross-workspace overview needs admin:read_system on the platform."
        />
      </AdminShell>
    )
  }

  const data = projects.data
  const filtered = filters.workspaceId !== undefined || filters.status !== undefined

  return (
    <AdminShell
      title="Projects"
      description="Every project across every workspace in this installation"
    >
      <div className="flex flex-wrap items-center gap-2">
        {workspaces.data ? (
          <WorkspacePicker
            id="projects-workspace"
            workspaces={workspaces.data}
            value={filters.workspaceId}
            onChange={(next) => setParam('workspaceId', next)}
            allowNone
          />
        ) : null}

        <Select
          value={filters.status ?? ANY}
          onValueChange={(value) => setParam('status', value === ANY ? undefined : value)}
        >
          <SelectTrigger className="h-8 w-[11rem]" aria-label="Filter by project status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY}>Any status</SelectItem>
            {PROJECT_STATUSES.map((status) => (
              <SelectItem key={status.value} value={status.value}>
                {status.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex items-center gap-2">
          <Label htmlFor="platform-project-sort" className="shrink-0 text-xs text-muted-foreground">
            Sort
          </Label>
          <Select value={sort} onValueChange={(value) => setParam('sort', value)}>
            <SelectTrigger id="platform-project-sort" className="h-8 w-[12rem]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PROJECT_SORTS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {filtered ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              const next = new URLSearchParams(searchParams)
              for (const key of ['workspaceId', 'status', 'page']) next.delete(key)
              setSearchParams(next, { replace: true })
            }}
          >
            Clear
          </Button>
        ) : null}
      </div>

      {projects.isError ? (
        <ErrorState error={projects.error} onRetry={() => void projects.refetch()} />
      ) : projects.isPending ? (
        <LoadingState label="Loading projects" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={FolderKanbanIcon}
          title={filtered ? 'No projects match' : 'No projects yet'}
          description={
            filtered
              ? 'Nothing in the installation matches these filters.'
              : 'Projects appear here as workspaces create them.'
          }
        />
      ) : data ? (
        <div className="space-y-4">
          <PlatformProjectTable projects={data.content} />
          <PaginationBar page={data} onPageChange={(next) => setParam('page', String(next + 1))} />
        </div>
      ) : null}
    </AdminShell>
  )
}
