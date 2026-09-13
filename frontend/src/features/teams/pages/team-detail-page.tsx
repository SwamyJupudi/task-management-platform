import { ArchiveIcon, ArchiveRestoreIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { paths } from '@/app/routes/paths'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PageHeader } from '@/components/common/page-header'
import { useSetBreadcrumbTitle } from '@/components/layout/breadcrumb-title'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'

import { TeamFormDialog } from '../components/team-form-dialog'
import { TeamMembersPanel } from '../components/team-members-panel'
import { useTeam, useTeamAbilities, useTeamMutations } from '../hooks'

/**
 * One team: its details, its roster and its lead.
 *
 * Every control is gated on the same rule the guard applies — the permission
 * code, plus either `team:manage_any` or leading this team. That answer is
 * exact here rather than approximate, because a team carries its own lead, so
 * nothing has to be fetched to work it out.
 *
 * Deleting is the exception a lead runs into: `team:delete` belongs to the
 * workspace administrator, so a lead can rename, archive and staff their team
 * without being able to remove it.
 */

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}

export function TeamDetailPage() {
  const { teamId } = useParams<{ teamId: string }>()
  const workspace = useActiveWorkspace()
  const navigate = useNavigate()

  const team = useTeam(teamId)
  const abilities = useTeamAbilities(team.data)
  const { setArchived, remove } = useTeamMutations(teamId)

  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useSetBreadcrumbTitle(team.data?.name)

  if (team.isError) {
    return <ErrorState error={team.error} onRetry={() => void team.refetch()} />
  }

  if (team.isPending) {
    return <LoadingState label="Loading the team" />
  }

  const data = team.data
  const slug = workspace?.workspaceSlug ?? ''
  const archived = data.status === 'ARCHIVED'

  return (
    <div className="space-y-6">
      <PageHeader
        title={data.name}
        description={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span>
              {data.memberCount.toLocaleString()} member{data.memberCount === 1 ? '' : 's'}
            </span>
            {archived ? (
              <Badge variant="outline" className="border-dashed">
                Archived
              </Badge>
            ) : null}
            {abilities.leadsIt ? <Badge variant="secondary">You lead this team</Badge> : null}
          </span>
        }
        actions={
          <>
            {abilities.canEdit ? (
              <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                <PencilIcon aria-hidden="true" />
                Edit
              </Button>
            ) : null}

            {abilities.canArchive ? (
              <Button
                variant="outline"
                size="sm"
                disabled={setArchived.isPending}
                onClick={async () => {
                  try {
                    await setArchived.mutateAsync({ id: data.id, archived: !archived })
                    toast.success(archived ? 'Team restored.' : 'Team archived.')
                  } catch (error) {
                    toast.error(toUserMessage(error))
                  }
                }}
              >
                {archived ? (
                  <>
                    <ArchiveRestoreIcon aria-hidden="true" />
                    Restore
                  </>
                ) : (
                  <>
                    <ArchiveIcon aria-hidden="true" />
                    Archive
                  </>
                )}
              </Button>
            ) : null}

            {abilities.canDelete ? (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Delete this team"
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

            <dl className="grid gap-4 sm:grid-cols-2">
              <Detail label="Lead">{data.leadName ?? 'Nobody yet'}</Detail>
              <Detail label="Members">{data.memberCount.toLocaleString()}</Detail>
              <Detail label="Status">{archived ? 'Archived' : 'Active'}</Detail>
              <Detail label="Created">
                {new Date(data.createdAt).toLocaleDateString(undefined, {
                  day: 'numeric',
                  month: 'short',
                  year: 'numeric',
                })}
              </Detail>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">Members</CardTitle>
          </CardHeader>
          <CardContent>
            <TeamMembersPanel team={data} canManage={abilities.canManageMembers} />
          </CardContent>
        </Card>
      </div>

      <TeamFormDialog open={editing} onOpenChange={setEditing} team={data} />

      <AlertDialog open={deleting} onOpenChange={setDeleting}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {data.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              It disappears from every listing and its name becomes available for a new team. Any
              project it runs keeps running without one. To shelve it instead and keep the roster,
              archive it.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={remove.isPending}
              onClick={async (event) => {
                // The dialog closes itself on action; the mutation decides when,
                // so a failure leaves it up with its message.
                event.preventDefault()
                try {
                  await remove.mutateAsync(data.id)
                  toast.success(`${data.name} deleted.`)
                  setDeleting(false)
                  void navigate(paths.workspace.teams(slug), { replace: true })
                } catch (error) {
                  toast.error(toUserMessage(error))
                }
              }}
            >
              {remove.isPending ? 'Deleting…' : 'Delete team'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
