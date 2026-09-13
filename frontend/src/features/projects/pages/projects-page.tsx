import { FolderKanbanIcon, LockIcon, PlusIcon } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { paths } from '@/app/routes/paths'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'
import { useSessionStore } from '@/stores/session-store'

import { ProjectBoard } from '../components/project-board'
import { ProjectFiltersBar } from '../components/project-filters'
import { ProjectList } from '../components/project-list'
import { PaginationBar } from '../components/pagination-bar'
import { ProjectFormDialog } from '../components/project-form-dialog'
import { DEFAULT_PAGE_SIZE, DEFAULT_SORT, isProjectPriority, isProjectStatus } from '../constants'
import {
  useChangeProjectStatus,
  useProjectPermissions,
  useProjects,
  useTeamOptions,
} from '../hooks'
import type { Project, ProjectFilters, ProjectStatus } from '../types'

/**
 * The project listing, as a table and as a board.
 *
 * Every filter, the sort and the page live in the query string rather than in
 * component state, so a narrowed listing is a link somebody can send and a
 * reload does not throw the work away. Nothing is filtered in the browser: the
 * list is paged server-side, and a client-side filter would narrow the page in
 * hand while appearing to narrow the whole set.
 *
 * The board renders the same page as the list rather than querying per column,
 * which keeps one set of filters over both views. Its column counts are counts
 * of the current page, and say so.
 */

type View = 'list' | 'board'

/** Reads the filters out of the URL, ignoring anything that is not a real value. */
function readFilters(params: URLSearchParams): ProjectFilters {
  const status = params.get('status')
  const priority = params.get('priority')
  const teamId = params.get('teamId')
  const ownerUserId = params.get('ownerUserId')
  const q = params.get('q')
  const label = params.get('label')

  return {
    ...(status && isProjectStatus(status) ? { status } : {}),
    ...(priority && isProjectPriority(priority) ? { priority } : {}),
    ...(teamId ? { teamId } : {}),
    ...(ownerUserId ? { ownerUserId } : {}),
    ...(q ? { q } : {}),
    ...(label ? { label } : {}),
  }
}

export function ProjectsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspace = useActiveWorkspace()
  const navigate = useNavigate()
  const permissions = useProjectPermissions()
  const teams = useTeamOptions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  const [creating, setCreating] = useState(false)
  const [pendingId, setPendingId] = useState<string | null>(null)

  const filters = useMemo(() => readFilters(searchParams), [searchParams])
  const sort = searchParams.get('sort') ?? DEFAULT_SORT
  const view: View = searchParams.get('view') === 'board' ? 'board' : 'list'
  const pageIndex = Math.max(Number(searchParams.get('page') ?? '1') - 1, 0)

  const pageRequest = useMemo(
    () => ({ page: pageIndex, size: DEFAULT_PAGE_SIZE, sort }),
    [pageIndex, sort],
  )

  const projects = useProjects(filters, pageRequest)
  const changeStatus = useChangeProjectStatus()

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
    (nextFilters: ProjectFilters) => {
      applyParams((params) => {
        for (const key of ['status', 'priority', 'teamId', 'ownerUserId', 'q', 'label'] as const) {
          const value = nextFilters[key]
          if (value === undefined || value === '') params.delete(key)
          else params.set(key, value)
        }
      })
    },
    [applyParams],
  )

  const onStatusChange = async (project: Project, status: ProjectStatus) => {
    setPendingId(project.id)
    try {
      await changeStatus.mutateAsync({ projectId: project.id, status })
      toast.success(`${project.name} moved.`)
    } catch (error) {
      // Includes the 409 the state machine answers with, which is the
      // backend's to explain rather than something to pre-empt here.
      toast.error(toUserMessage(error))
    } finally {
      setPendingId(null)
    }
  }

  /** Mirrors the guard: the code, plus manage_any or owning it or leading its team. */
  const canChangeStatus = (project: Project) => {
    if (!permissions.canUpdateAny) return false
    if (permissions.manageAny) return true
    if (project.ownerUserId !== null && project.ownerUserId === userId) return true
    return (
      project.teamId !== null &&
      teams.data?.some((team) => team.id === project.teamId && team.leadUserId === userId) === true
    )
  }

  const header = (
    <PageHeader
      title="Projects"
      description={workspace ? `In ${workspace.workspaceName}` : undefined}
      actions={
        permissions.canCreate ? (
          <Button onClick={() => setCreating(true)}>
            <PlusIcon aria-hidden="true" />
            New project
          </Button>
        ) : undefined
      }
    />
  )

  // Reading projects needs project:read. Saying so beats a red panel holding a
  // 403 the user can do nothing about.
  if (!permissions.canRead) {
    return (
      <div className="space-y-6">
        {header}
        <EmptyState
          icon={LockIcon}
          title="You cannot see projects here"
          description="Your role in this workspace does not include reading projects. An administrator can grant it."
        />
      </div>
    )
  }

  const page = projects.data
  const slug = workspace?.workspaceSlug ?? ''
  const filtered = Object.keys(filters).length > 0

  return (
    <div className="space-y-6">
      {header}

      <Tabs
        value={view}
        onValueChange={(next) =>
          applyParams((params) => {
            if (next === 'list') params.delete('view')
            else params.set('view', next)
          }, false)
        }
        className="space-y-4"
      >
        <div className="flex flex-col gap-3">
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="board">Board</TabsTrigger>
          </TabsList>

          <ProjectFiltersBar
            filters={filters}
            sort={sort}
            onFiltersChange={onFiltersChange}
            onSortChange={(next) => applyParams((params) => params.set('sort', next))}
          />
        </div>

        {projects.isError ? (
          <ErrorState error={projects.error} onRetry={() => void projects.refetch()} />
        ) : projects.isPending ? (
          <LoadingState label="Loading projects" />
        ) : page && page.content.length === 0 ? (
          <EmptyState
            icon={FolderKanbanIcon}
            title={filtered ? 'No projects match those filters' : 'No projects yet'}
            description={
              filtered
                ? 'Try widening the search, or clear the filters to see everything you can reach.'
                : 'Projects you create or are added to will appear here.'
            }
            action={
              !filtered && permissions.canCreate ? (
                <Button onClick={() => setCreating(true)}>
                  <PlusIcon aria-hidden="true" />
                  New project
                </Button>
              ) : undefined
            }
          />
        ) : page ? (
          <>
            <TabsContent value="list" className="space-y-4">
              <ProjectList
                projects={page.content}
                workspaceSlug={slug}
                onLabelSelect={(label) => applyParams((params) => params.set('label', label))}
              />
              <PaginationBar
                page={page}
                onPageChange={(next) =>
                  applyParams((params) => params.set('page', String(next + 1)), false)
                }
              />
            </TabsContent>

            <TabsContent value="board" className="space-y-4">
              <ProjectBoard
                projects={page.content}
                workspaceSlug={slug}
                canChangeStatus={canChangeStatus}
                onStatusChange={onStatusChange}
                pendingId={pendingId}
              />
              <PaginationBar
                page={page}
                onPageChange={(next) =>
                  applyParams((params) => params.set('page', String(next + 1)), false)
                }
              />
            </TabsContent>
          </>
        ) : null}
      </Tabs>

      <ProjectFormDialog
        open={creating}
        onOpenChange={setCreating}
        onCreated={(created) => void navigate(paths.workspace.project(slug, created.id))}
      />
    </div>
  )
}
