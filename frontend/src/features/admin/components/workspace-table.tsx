import { MoreHorizontalIcon } from 'lucide-react'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { toUserMessage } from '@/lib/api'
import { relativeTime } from '@/lib/datetime'
import { useSessionStore } from '@/stores/session-store'

import { useAdminPermissions, useWorkspaceMutations } from '../hooks'
import type { WorkspaceSummary } from '../types'

/**
 * Every workspace in the installation.
 *
 * A row you belong to is marked. Membership and administration are separate
 * things here — a platform administrator can edit and remove a workspace they
 * have never joined — and the badge is what keeps the two legible: it says why
 * some rows appear in the workspace switcher and others do not.
 *
 * Rows do not link into the workspace. `/w/{slug}` resolves against membership
 * rather than permission, so a link from a row you are not in would land on the
 * not-found screen. The ones you are in do link, because those work.
 *
 * Archiving is not offered here. It is a workspace-level decision with its own
 * permission and it lives on that workspace's own settings screen; duplicating
 * it would mean two controls with different reach for one operation.
 */
export function WorkspaceTable({ workspaces }: { workspaces: WorkspaceSummary[] }) {
  const { canDeleteWorkspaces } = useAdminPermissions()
  const { remove } = useWorkspaceMutations()
  const memberships = useSessionStore((state) => state.memberships)

  const [deleting, setDeleting] = useState<WorkspaceSummary | null>(null)

  return (
    <>
      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Workspace</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Default role</TableHead>
              <TableHead>Time zone</TableHead>
              <TableHead>Created</TableHead>
              <TableHead className="w-12 text-right">
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {workspaces.map((workspace) => {
              const mine = memberships.some((membership) => membership.workspaceId === workspace.id)

              return (
                <TableRow key={workspace.id}>
                  <TableCell className="max-w-[20rem]">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <span className="truncate font-medium">{workspace.name}</span>
                      {mine ? <Badge variant="outline">You are a member</Badge> : null}
                    </span>
                    <span className="block truncate font-mono text-xs text-muted-foreground">
                      {workspace.slug}
                    </span>
                  </TableCell>

                  <TableCell>
                    <Badge variant={workspace.status === 'ARCHIVED' ? 'secondary' : 'outline'}>
                      {workspace.status === 'ARCHIVED' ? 'Archived' : 'Active'}
                    </Badge>
                  </TableCell>

                  <TableCell className="text-muted-foreground">
                    {workspace.defaultRoleSlug}
                  </TableCell>

                  <TableCell className="text-muted-foreground">{workspace.timezone}</TableCell>

                  <TableCell className="text-xs whitespace-nowrap text-muted-foreground">
                    <time
                      dateTime={workspace.createdAt}
                      title={new Date(workspace.createdAt).toLocaleString()}
                    >
                      {relativeTime(workspace.createdAt)}
                    </time>
                  </TableCell>

                  <TableCell className="text-right">
                    {canDeleteWorkspaces ? (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="sm"
                            aria-label={`Actions for ${workspace.name}`}
                          >
                            <MoreHorizontalIcon aria-hidden="true" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-56">
                          <DropdownMenuLabel className="truncate">
                            {workspace.name}
                          </DropdownMenuLabel>
                          <DropdownMenuItem
                            variant="destructive"
                            onSelect={() => setDeleting(workspace)}
                          >
                            Delete workspace
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    ) : null}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {deleting?.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              It disappears from every listing and its address{' '}
              <span className="font-mono">{deleting?.slug}</span> becomes available for a new
              workspace. Everything inside it — projects, tasks, comments and history — goes with
              it. To freeze it instead and keep it readable, archive it from its own settings.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={remove.isPending}
              onClick={(event) => {
                // The mutation decides when the dialog closes, so a refusal
                // leaves it up with its message rather than vanishing.
                event.preventDefault()
                if (!deleting) return
                remove
                  .mutateAsync(deleting.id)
                  .then(() => {
                    toast.success(`${deleting.name} was deleted.`)
                    setDeleting(null)
                  })
                  .catch((error: unknown) => toast.error(toUserMessage(error)))
              }}
            >
              {remove.isPending ? 'Deleting…' : 'Delete workspace'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
