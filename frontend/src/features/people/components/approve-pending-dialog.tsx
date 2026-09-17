import { useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FormError } from '@/features/auth/components/form-error'
import { useProjects } from '@/features/projects/hooks'
import type { Project } from '@/features/projects/types'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'

import { useApprovePendingUser, useWorkspaceRoles } from '../hooks'
import type { PendingUser } from '../types'

/**
 * Admitting somebody to this workspace.
 *
 * The workspace is not a choice here, unlike the platform panel's version of
 * this dialog: it is the one being administered, which is also the one the
 * caller's permission was checked against. Everything this fetches is
 * workspace-scoped for the same reason — a workspace administrator holds no
 * platform permission and cannot read the installation-wide lists.
 *
 * The role list comes from the workspace rather than a hardcoded three, because
 * a workspace's roles can be edited and the choice should be what it actually
 * has.
 */
export function ApprovePendingDialog({
  account,
  open,
  onOpenChange,
}: {
  account: PendingUser | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [roleSlug, setRoleSlug] = useState<string | undefined>(undefined)
  const [projectId, setProjectId] = useState<string | undefined>(undefined)

  const workspace = useActiveWorkspace()
  const roles = useWorkspaceRoles()
  const projects = useProjects({}, { page: 0, size: 100, sort: 'createdAt,desc' })
  const approve = useApprovePendingUser()

  const close = (next: boolean) => {
    if (!next) {
      setRoleSlug(undefined)
      setProjectId(undefined)
      approve.reset()
    }
    onOpenChange(next)
  }

  const submit = () => {
    if (!account || !roleSlug) return
    approve.mutate({ userId: account.id, roleSlug, projectId }, { onSuccess: () => close(false) })
  }

  const projectOptions = projects.data?.content ?? []

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            Approve {account ? `${account.firstName} ${account.lastName}` : 'account'}
          </DialogTitle>
          <DialogDescription>
            {account?.email} joins {workspace?.workspaceName ?? 'this workspace'} and can sign in to it
            straight away.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <FormError error={approve.error} title="Could not approve this account" />

          <div className="space-y-2">
            <Label htmlFor="pending-role">Role</Label>
            <Select value={roleSlug} onValueChange={setRoleSlug}>
              <SelectTrigger id="pending-role" className="w-full">
                <SelectValue placeholder="Choose a role" />
              </SelectTrigger>
              <SelectContent>
                {(roles.data ?? []).map((role) => (
                  <SelectItem key={role.id} value={role.slug}>
                    {role.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="pending-project">Project (optional)</Label>
            <Select value={projectId} onValueChange={setProjectId}>
              <SelectTrigger id="pending-project" className="w-full">
                <SelectValue placeholder="No project for now" />
              </SelectTrigger>
              <SelectContent>
                {projectOptions.map((project: Project) => (
                  <SelectItem key={project.id} value={project.id}>
                    {project.key} — {project.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => close(false)} disabled={approve.isPending}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!roleSlug || approve.isPending}>
            {approve.isPending ? 'Approving…' : 'Approve'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
