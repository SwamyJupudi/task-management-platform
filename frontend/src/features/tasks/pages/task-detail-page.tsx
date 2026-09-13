import { PencilIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { paths } from '@/app/routes/paths'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { useSetBreadcrumbTitle } from '@/components/layout/breadcrumb-title'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'

import { AssigneePicker } from '../components/assignee-picker'
import { BlockedBadge, TaskPriorityBadge, TaskStatusBadge } from '../components/task-badges'
import { TaskFormDialog } from '../components/task-form-dialog'
import { TaskStatusMenu } from '../components/task-status-menu'
import { formatMinutes } from '../constants'
import { useChangeTaskStatus, useOwnsTaskProject, useTask, useTaskAbilities } from '../hooks'
import type { Task, TaskStatus } from '../types'

/**
 * One task: its details, who holds it, and the actions the caller may take.
 *
 * Every control is gated on the same rule the backend applies, and on the right
 * code: moving a task needs `task:change_status`, assigning needs `task:assign`
 * and editing needs `task:update`, so the three appear independently rather
 * than together behind one "can edit".
 *
 * Dependencies are shown and not edited. The graph has its own endpoints and is
 * a later phase; what is useful now is knowing that something is waiting.
 * Subtasks, comments, attachments and activity are likewise not here.
 */

function formatDate(value: string | null): string {
  if (value === null) return 'Not set'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return 'Not set'
  return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}

/** The dependency lists, read-only. */
function LinkedTasks({
  title,
  links,
  workspaceSlug,
}: {
  title: string
  links: Task['blockedBy']
  workspaceSlug: string
}) {
  if (links.length === 0) return null

  return (
    <div className="space-y-1.5">
      <p className="text-xs text-muted-foreground">{title}</p>
      <ul className="space-y-1">
        {links.map((link) => (
          <li key={link.taskId} className="flex items-center gap-2 text-sm">
            <Link
              to={paths.workspace.task(workspaceSlug, link.taskId)}
              className="shrink-0 font-mono text-xs text-muted-foreground hover:underline"
            >
              {link.key}
            </Link>
            <span className="min-w-0 truncate">{link.title}</span>
            <TaskStatusBadge status={link.status} className="ml-auto shrink-0" />
          </li>
        ))}
      </ul>
    </div>
  )
}

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const workspace = useActiveWorkspace()

  const task = useTask(taskId)
  const ownsProject = useOwnsTaskProject(task.data?.projectId)
  const abilities = useTaskAbilities(task.data, ownsProject)
  const changeStatus = useChangeTaskStatus()

  const [editing, setEditing] = useState(false)

  // Names the final breadcrumb, so the trail reads the task key rather than its
  // identifier. Undefined while it loads, which leaves the path segment in
  // place until the key arrives.
  useSetBreadcrumbTitle(task.data?.key)

  if (task.isError) {
    return <ErrorState error={task.error} onRetry={() => void task.refetch()} />
  }

  if (task.isPending) {
    return <LoadingState label="Loading the task" />
  }

  const data = task.data
  const slug = workspace?.workspaceSlug ?? ''

  const onStatusChange = async (status: TaskStatus) => {
    try {
      await changeStatus.mutateAsync({ taskId: data.id, status })
      toast.success('Status updated.')
    } catch (error) {
      // Includes the 409 the state machine answers with, which is the
      // backend's to explain rather than something to pre-empt here.
      toast.error(toUserMessage(error))
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={data.title}
        description={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-mono">{data.key}</span>
            <span aria-hidden="true">·</span>
            <Link
              to={paths.workspace.project(slug, data.projectId)}
              className="hover:text-foreground hover:underline"
            >
              {data.projectName}
            </Link>
            <TaskStatusBadge status={data.status} />
            <TaskPriorityBadge priority={data.priority} />
            {data.blocked ? <BlockedBadge /> : null}
          </span>
        }
        actions={
          <>
            {abilities.canChangeStatus ? (
              <TaskStatusMenu
                task={data}
                disabled={changeStatus.isPending}
                onSelect={onStatusChange}
              />
            ) : null}

            {abilities.canEdit ? (
              <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                <PencilIcon aria-hidden="true" />
                Edit
              </Button>
            ) : null}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-sm font-medium">Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {data.description ? (
              <p className="text-sm whitespace-pre-wrap">{data.description}</p>
            ) : (
              <p className="text-sm text-muted-foreground">No description.</p>
            )}

            <Separator />

            <dl className="grid gap-4 sm:grid-cols-2">
              <Detail label="Reporter">{data.reporterName ?? 'No longer in the workspace'}</Detail>
              <Detail label="Project">{data.projectName}</Detail>
              <Detail label="Starts">{formatDate(data.startDate)}</Detail>
              <Detail label="Due">{formatDate(data.dueDate)}</Detail>
              <Detail label="Estimate">{formatMinutes(data.estimatedMinutes)}</Detail>
              <Detail label="Time spent">{formatMinutes(data.actualMinutes)}</Detail>
              <Detail label="Labels">
                {data.labels.length === 0 ? (
                  'None'
                ) : (
                  <span className="flex flex-wrap gap-1">
                    {data.labels.map((label) => (
                      <Badge key={label} variant="secondary">
                        {label}
                      </Badge>
                    ))}
                  </span>
                )}
              </Detail>
              {data.completedAt ? (
                <Detail label="Completed">{new Date(data.completedAt).toLocaleString()}</Detail>
              ) : null}
            </dl>

            {data.blockedBy.length > 0 || data.blocking.length > 0 ? (
              <>
                <Separator />
                <div className="space-y-4">
                  <LinkedTasks title="Waiting on" links={data.blockedBy} workspaceSlug={slug} />
                  <LinkedTasks title="Blocking" links={data.blocking} workspaceSlug={slug} />
                </div>
              </>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Assignee</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {abilities.canAssign ? (
              <AssigneePicker task={data} />
            ) : (
              <p className="text-sm">{data.assigneeName ?? 'Unassigned'}</p>
            )}

            <p className="text-xs text-muted-foreground">
              Only members of {data.projectName} can hold this task.
            </p>
          </CardContent>
        </Card>
      </div>

      <TaskFormDialog open={editing} onOpenChange={setEditing} task={data} />
    </div>
  )
}
