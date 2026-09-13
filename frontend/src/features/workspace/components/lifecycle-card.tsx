import { ArchiveIcon, ArchiveRestoreIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { toUserMessage } from '@/lib/api'

import { useWorkspaceMutations, useWorkspacePermissions } from '../hooks'
import type { Workspace } from '../types'

/**
 * Archiving, and coming back from it.
 *
 * A lifecycle decision about the whole workspace rather than an edit to one of
 * its fields, which is why the backend gives it its own permission and why it
 * sits in its own card rather than as a fifth control in the settings form.
 *
 * **Archiving freezes every change inside the workspace**, not only these
 * settings: projects, tasks, comments, invitations and role edits all begin
 * answering 409. That is stated before the confirmation rather than discovered
 * afterwards, because it is the consequence somebody needs to weigh.
 *
 * Deleting is deliberately absent. `workspace:delete` is granted to no
 * workspace role — removing a workspace is platform administration — so the
 * control lives in the admin panel and offering a disabled one here would
 * suggest a workspace administrator might earn it.
 */
export function LifecycleCard({ workspace }: { workspace: Workspace }) {
  const { canArchive } = useWorkspacePermissions()
  const { archive, unarchive } = useWorkspaceMutations()

  const [confirming, setConfirming] = useState(false)

  const archived = workspace.status === 'ARCHIVED'
  const pending = archive.isPending || unarchive.isPending

  const run = (action: Promise<unknown>, success: string) => {
    action
      .then(() => {
        toast.success(success)
        setConfirming(false)
      })
      .catch((error: unknown) => toast.error(toUserMessage(error)))
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex flex-wrap items-center gap-2">
            Lifecycle
            <Badge variant={archived ? 'secondary' : 'outline'}>
              {archived ? 'Archived' : 'Active'}
            </Badge>
          </CardTitle>
          <CardDescription>
            {archived
              ? 'This workspace is frozen. Everything in it stays readable and nothing in it can be changed.'
              : 'Archiving freezes the workspace. Nothing is deleted and it can be restored at any time.'}
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-3">
          {archived && workspace.archivedAt ? (
            <p className="text-sm text-muted-foreground">
              Archived on{' '}
              <time dateTime={workspace.archivedAt}>
                {new Date(workspace.archivedAt).toLocaleDateString()}
              </time>
              .
            </p>
          ) : null}

          {canArchive ? (
            archived ? (
              <Button
                variant="outline"
                disabled={pending}
                onClick={() => run(unarchive.mutateAsync(), 'The workspace was restored.')}
              >
                <ArchiveRestoreIcon aria-hidden="true" />
                {unarchive.isPending ? 'Restoring…' : 'Restore workspace'}
              </Button>
            ) : (
              <Button variant="outline" disabled={pending} onClick={() => setConfirming(true)}>
                <ArchiveIcon aria-hidden="true" />
                Archive workspace
              </Button>
            )
          ) : (
            <p className="text-sm text-muted-foreground">
              Archiving needs workspace:archive in this workspace.
            </p>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive {workspace.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              Everybody keeps their access and everything stays readable, but nothing in the
              workspace can be changed while it is archived — no projects, tasks, comments,
              invitations or role edits. You can restore it from this screen at any time.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={archive.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={archive.isPending}
              onClick={(event) => {
                // The mutation decides when the dialog closes, so a refusal
                // leaves it up with its message rather than vanishing.
                event.preventDefault()
                run(archive.mutateAsync(), 'The workspace was archived.')
              }}
            >
              {archive.isPending ? 'Archiving…' : 'Archive'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
