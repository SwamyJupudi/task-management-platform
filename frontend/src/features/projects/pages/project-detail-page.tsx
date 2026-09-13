import { PencilIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { paths } from '@/app/routes/paths'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Separator } from '@/components/ui/separator'
import { useSetBreadcrumbTitle } from '@/components/layout/breadcrumb-title'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'

import { DeleteProjectDialog } from '../components/delete-project-dialog'
import { ProjectPriorityBadge, ProjectStatusBadge } from '../components/project-badges'
import { ProjectFormDialog } from '../components/project-form-dialog'
import { ProjectMembersPanel } from '../components/project-members-panel'
import { StatusMenu } from '../components/status-menu'
import {
  useChangeProjectStatus,
  useDeleteProject,
  useProject,
  useProjectAbilities,
  useTeamOptions,
} from '../hooks'
import type { ProjectStatus } from '../types'

/**
 * One project: its details, its roster, and the actions the caller may take.
 *
 * Every control is gated on the same rule the backend applies — the permission
 * code, plus either `project:manage_any` or owning the project or leading its
 * team. Hiding a control the API would refuse is a courtesy; the API refuses it
 * again regardless, and a refusal that slips through is shown as it comes.
 *
 * There is no task list here. Tasks are their own feature and their own phase.
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

export function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const workspace = useActiveWorkspace()
  const navigate = useNavigate()

  const project = useProject(projectId)
  const teams = useTeamOptions()
  const abilities = useProjectAbilities(project.data, teams.data)

  const changeStatus = useChangeProjectStatus()
  const remove = useDeleteProject()

  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  // Names the final breadcrumb, so the trail reads the project rather than its
  // identifier. Undefined while it loads, which leaves the path segment in
  // place until the name arrives.
  useSetBreadcrumbTitle(project.data?.name)

  if (project.isError) {
    return <ErrorState error={project.error} onRetry={() => void project.refetch()} />
  }

  if (project.isPending) {
    return <LoadingState label="Loading the project" />
  }

  const data = project.data
  const slug = workspace?.workspaceSlug ?? ''

  const onStatusChange = async (status: ProjectStatus) => {
    try {
      await changeStatus.mutateAsync({ projectId: data.id, status })
      toast.success('Status updated.')
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={data.name}
        description={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-mono">{data.key}</span>
            <span aria-hidden="true">·</span>
            <ProjectStatusBadge status={data.status} />
            <ProjectPriorityBadge priority={data.priority} />
          </span>
        }
        actions={
          <>
            {abilities.canChangeStatus ? (
              <StatusMenu
                project={data}
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

            {abilities.canDelete ? (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Delete this project"
                onClick={() => setDeleting(true)}
              >
                <Trash2Icon aria-hidden="true" />
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

            <div className="space-y-2">
              <div className="flex items-baseline justify-between">
                <span className="text-xs text-muted-foreground">Progress</span>
                <span className="text-sm font-medium tabular-nums">{data.progress}%</span>
              </div>
              <Progress
                value={data.progress}
                aria-label={`${data.name}: ${data.progress}% complete`}
              />
              <p className="text-xs text-muted-foreground">
                Derived from the project's tasks by the platform.
              </p>
            </div>

            <Separator />

            <dl className="grid gap-4 sm:grid-cols-2">
              <Detail label="Owner">{data.ownerName ?? 'Nobody yet'}</Detail>
              <Detail label="Team">{data.teamName ?? 'No team'}</Detail>
              <Detail label="Starts">{formatDate(data.startDate)}</Detail>
              <Detail label="Ends">{formatDate(data.endDate)}</Detail>
              <Detail label="Members">{data.memberCount.toLocaleString()}</Detail>
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
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Members</CardTitle>
          </CardHeader>
          <CardContent>
            <ProjectMembersPanel project={data} canManage={abilities.canManageMembers} />
          </CardContent>
        </Card>
      </div>

      <ProjectFormDialog open={editing} onOpenChange={setEditing} project={data} />

      <DeleteProjectDialog
        project={data}
        open={deleting}
        onOpenChange={setDeleting}
        pending={remove.isPending}
        onConfirm={async () => {
          try {
            await remove.mutateAsync(data.id)
            toast.success(`${data.name} deleted.`)
            setDeleting(false)
            void navigate(paths.workspace.projects(slug), { replace: true })
          } catch (error) {
            toast.error(toUserMessage(error))
          }
        }}
      />
    </div>
  )
}
