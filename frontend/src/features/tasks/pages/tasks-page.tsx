import { ListChecksIcon, LockIcon, PlusIcon } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { useSessionStore } from '@/stores/session-store'

import { TaskFiltersBar } from '../components/task-filters'
import { TaskFormDialog } from '../components/task-form-dialog'
import { TaskList } from '../components/task-list'
import { DEFAULT_PAGE_SIZE, DEFAULT_SORT, isTaskPriority, isTaskStatus } from '../constants'
import { useTaskPermissions, useTasks } from '../hooks'
import type { TaskFilters } from '../types'

/**
 * The task listing, in two guises: everything the caller can reach, and their
 * own work.
 *
 * One component because My Tasks is the same screen with the assignee pinned to
 * the signed-in person. Splitting it would mean two copies of the filters, the
 * sort and the paging to keep in step, and the endpoint already answers both
 * questions.
 *
 * Every filter, the sort and the page live in the query string rather than in
 * component state, so a narrowed listing is a link somebody can send and a
 * reload does not throw the work away. Nothing is filtered in the browser.
 */

/** Reads the filters out of the URL, ignoring anything that is not a real value. */
function readFilters(params: URLSearchParams): TaskFilters {
  const status = params.get('status')
  const priority = params.get('priority')
  const projectId = params.get('projectId')
  const label = params.get('label')
  const q = params.get('q')

  return {
    ...(status && isTaskStatus(status) ? { status } : {}),
    ...(priority && isTaskPriority(priority) ? { priority } : {}),
    ...(projectId ? { projectId } : {}),
    ...(label ? { label } : {}),
    ...(q ? { q } : {}),
    ...(params.get('overdue') === 'true' ? { overdue: true } : {}),
    ...(params.get('unassigned') === 'true' ? { unassigned: true } : {}),
  }
}

const FILTER_KEYS = ['status', 'priority', 'projectId', 'label', 'q'] as const
const TOGGLE_KEYS = ['overdue', 'unassigned'] as const

export function TasksPage({ mine = false }: { mine?: boolean }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()
  const navigate = useNavigate()
  const permissions = useTaskPermissions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  const [creating, setCreating] = useState(false)

  const urlFilters = useMemo(() => readFilters(searchParams), [searchParams])
  const sort = searchParams.get('sort') ?? DEFAULT_SORT
  const pageIndex = Math.max(Number(searchParams.get('page') ?? '1') - 1, 0)

  // My Tasks pins the assignee rather than offering it as a filter, so the
  // screen cannot be turned into somebody else's list by editing the URL.
  const filters = useMemo<TaskFilters>(
    () => (mine && userId !== null ? { ...urlFilters, assigneeUserId: userId } : urlFilters),
    [mine, userId, urlFilters],
  )

  const pageRequest = useMemo(
    () => ({ page: pageIndex, size: DEFAULT_PAGE_SIZE, sort }),
    [pageIndex, sort],
  )

  const tasks = useTasks(filters, pageRequest)

  /** Writes the URL, and resets to the first page whenever the result set changes. */
  const applyParams = useCallback(
    (mutate: (params: URLSearchParams) => void, resetPage = true) => {
      const next = new URLSearchParams(searchParams)
      mutate(next)
      if (resetPage) next.delete('page')
      setSearchParams(next, { replace: true })
    },
    [searchParams, setSearchParams],
  )

  const onFiltersChange = useCallback(
    (nextFilters: TaskFilters) => {
      applyParams((params) => {
        for (const key of FILTER_KEYS) {
          const value = nextFilters[key]
          if (value === undefined || value === '') params.delete(key)
          else params.set(key, value)
        }
        for (const key of TOGGLE_KEYS) {
          if (nextFilters[key] === true) params.set(key, 'true')
          else params.delete(key)
        }
      })
    },
    [applyParams],
  )

  const header = (
    <PageHeader
      title={mine ? 'My tasks' : 'Tasks'}
      description={
        mine
          ? 'Everything assigned to you in this workspace'
          : workspace
            ? `In ${workspace.workspaceName}`
            : undefined
      }
      actions={
        permissions.canCreate ? (
          <Button onClick={() => setCreating(true)}>
            <PlusIcon aria-hidden="true" />
            New task
          </Button>
        ) : undefined
      }
    />
  )

  // Reading tasks needs task:read. Saying so beats a red panel holding a 403
  // the user can do nothing about.
  if (!permissions.canRead) {
    return (
      <div className="space-y-6">
        {header}
        <EmptyState
          icon={LockIcon}
          title="You cannot see tasks here"
          description="Your role in this workspace does not include reading tasks. An administrator can grant it."
        />
      </div>
    )
  }

  const page = tasks.data
  const slug = workspace?.workspaceSlug ?? ''
  const filtered = Object.keys(urlFilters).length > 0

  return (
    <div className="space-y-6">
      {header}

      <TaskFiltersBar
        filters={urlFilters}
        sort={sort}
        onFiltersChange={onFiltersChange}
        onSortChange={(next) => applyParams((params) => params.set('sort', next))}
      />

      {tasks.isError ? (
        <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />
      ) : tasks.isPending ? (
        <LoadingState label="Loading tasks" />
      ) : page && page.content.length === 0 ? (
        <EmptyState
          icon={ListChecksIcon}
          title={
            filtered
              ? 'No tasks match those filters'
              : mine
                ? 'Nothing is assigned to you'
                : 'No tasks yet'
          }
          description={
            filtered
              ? 'Try widening the search, or clear the filters to see everything you can reach.'
              : mine
                ? 'Work assigned to you will appear here.'
                : 'Tasks raised in projects you can reach will appear here.'
          }
          action={
            !filtered && !mine && permissions.canCreate ? (
              <Button onClick={() => setCreating(true)}>
                <PlusIcon aria-hidden="true" />
                New task
              </Button>
            ) : undefined
          }
        />
      ) : page ? (
        <div className="space-y-4">
          <TaskList
            tasks={page.content}
            workspaceSlug={slug}
            onLabelSelect={(label) => applyParams((params) => params.set('label', label))}
          />
          <PaginationBar
            page={page}
            onPageChange={(next) =>
              applyParams((params) => params.set('page', String(next + 1)), false)
            }
          />
        </div>
      ) : null}

      <TaskFormDialog
        open={creating}
        onOpenChange={setCreating}
        {...(urlFilters.projectId ? { defaultProjectId: urlFilters.projectId } : {})}
        onCreated={(created) => void navigate(paths.workspace.task(slug, created.id))}
      />
    </div>
  )
}
