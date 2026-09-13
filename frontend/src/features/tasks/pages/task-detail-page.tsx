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
import { CollaborationPanel } from '@/features/collaboration'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'

import { AssigneePicker } from '../components/assignee-picker'
import { DependenciesPanel } from '../components/dependencies-panel'
import { SubtaskPanel } from '../components/subtask-panel'
import { BlockedBadge, TaskPriorityBadge, TaskStatusBadge } from '../components/task-badges'
import { TaskFormDialog } from '../components/task-form-dialog'
import { TaskStatusMenu } from '../components/task-status-menu'
import { formatMinutes } from '../constants'
import { useChangeTaskStatus, useOwnsTaskProject, useTask, useTaskAbilities } from '../hooks'
import type { TaskStatus } from '../types'

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

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Checklist</CardTitle>
          </CardHeader>
          <CardContent>
            <SubtaskPanel
              task={data}
              canEdit={abilities.canEdit}
              canTick={abilities.canChangeStatus}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Dependencies</CardTitle>
          </CardHeader>
          <CardContent>
            <DependenciesPanel task={data} workspaceSlug={slug} canEdit={abilities.canEdit} />
          </CardContent>
        </Card>
      </div>

      <CollaborationPanel taskId={data.id} projectId={data.projectId} ownsProject={ownsProject} />

      <TaskFormDialog open={editing} onOpenChange={setEditing} task={data} />
    </div>
  )
}
