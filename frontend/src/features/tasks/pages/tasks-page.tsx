import { ListChecksIcon, LockIcon, PlusIcon } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { paths } from '@/app/routes/paths'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'
import { useSessionStore } from '@/stores/session-store'

import { TaskBoard } from '../components/task-board'
import { TaskFiltersBar } from '../components/task-filters'
import { TaskFormDialog } from '../components/task-form-dialog'
import { TaskList } from '../components/task-list'
import {
  DEFAULT_PAGE_SIZE,
  DEFAULT_SORT,
  STATUS_LABELS,
  isTaskPriority,
  isTaskStatus,
} from '../constants'
import { useChangeTaskStatus, useTaskPermissions, useTasks } from '../hooks'
import type { Task, TaskFilters, TaskStatus } from '../types'

/**
 * How many tasks the board asks for.
 *
 * A board grouped by status is only useful if the columns hold everything the
 * filter matched, so it asks for more than a page of twenty. It is still a
 * bound rather than "all": past it the board says how many it is showing and
 * suggests narrowing, instead of quietly drawing a partial picture.
 */
const BOARD_PAGE_SIZE = 100

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

type View = 'list' | 'board'

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

  const view: View = searchParams.get('view') === 'board' ? 'board' : 'list'

  const pageRequest = useMemo(
    () => ({
      // The board reads one large page and groups it; the list pages normally.
      page: view === 'board' ? 0 : pageIndex,
      size: view === 'board' ? BOARD_PAGE_SIZE : DEFAULT_PAGE_SIZE,
      sort: view === 'board' ? 'boardPosition,asc' : sort,
    }),
    [view, pageIndex, sort],
  )

  const tasks = useTasks(filters, pageRequest)
  const changeStatus = useChangeTaskStatus()
  const [pendingId, setPendingId] = useState<string | null>(null)

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

  const onStatusChange = async (task: Task, status: TaskStatus) => {
    setPendingId(task.id)
    try {
      await changeStatus.mutateAsync({ taskId: task.id, status })
      toast.success(`${task.key} moved to ${STATUS_LABELS[status]}.`)
    } catch (error) {
      // Includes the 409 the state machine answers with. The board refuses an
      // illegal drop before it starts, so this mostly catches a permission
      // refusal or a task somebody else moved first.
      toast.error(toUserMessage(error))
    } finally {
      setPendingId(null)
    }
  }

  /**
   * Whether the caller may move one particular card.
   *
   * The same compound rule the guard applies, as far as a listing can answer
   * it: the code, plus manage_any or being the assignee or the reporter. The
   * project owner and team lead cases need the project, which a listing does
   * not carry, so those see the control hidden and use the detail screen.
   */
  const canMove = (task: Task) => {
    if (!permissions.canChangeStatus) return false
    if (permissions.manageAny) return true
    if (userId === null) return false
    return task.assigneeUserId === userId || task.reporterUserId === userId
  }

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

      <div className="space-y-3">
        <Tabs
          value={view}
          onValueChange={(next) =>
            applyParams((params) => {
              if (next === 'list') params.delete('view')
              else params.set('view', next)
              // The board reads one large page from the start, so a page number
              // carried over from the list would be meaningless on it.
              params.delete('page')
            }, false)
          }
        >
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="board">Board</TabsTrigger>
          </TabsList>
        </Tabs>

        <TaskFiltersBar
          filters={urlFilters}
          sort={sort}
          onFiltersChange={onFiltersChange}
          onSortChange={(next) => applyParams((params) => params.set('sort', next))}
        />
      </div>

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
      ) : page && view === 'board' ? (
        <div className="space-y-3">
          <TaskBoard
            tasks={page.content}
            workspaceSlug={slug}
            canMove={canMove}
            onStatusChange={onStatusChange}
            pendingId={pendingId}
          />
          {page.totalElements > page.content.length ? (
            <p className="text-xs text-muted-foreground">
              Showing the first {page.content.length.toLocaleString()} of{' '}
              {page.totalElements.toLocaleString()} matching tasks. Narrow the filters to see a
              complete board.
            </p>
          ) : null}
        </div>
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
