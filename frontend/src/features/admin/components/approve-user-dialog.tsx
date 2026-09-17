import { useState } from 'react'

import { FormError } from '@/features/auth/components/form-error'
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

import { WORKSPACE_OPTION_SIZE } from '../constants'
import { useApproveAccount, usePlatformProjects, useWorkspaceRoles, useWorkspaces } from '../hooks'
import type { PlatformAccount } from '../types'

/**
 * Letting somebody in, and deciding where they land.
 *
 * The workspace and the role are asked for together because the server requires
 * both: approving without them would produce an account that can sign in and
 * reach nothing, which is the state approval exists to end. The project is
 * optional and deliberately so — an administrator who does not yet know what
 * somebody will work on should not have to invent an answer.
 *
 * The role list is fetched per workspace rather than hardcoded. Each workspace
 * has its own copy of the seeded roles, so the identifiers differ even though
 * the slugs do not, and a workspace whose roles have been edited should offer
 * what it actually has.
 */
export function ApproveUserDialog({
  account,
  open,
  onOpenChange,
}: {
  account: PlatformAccount | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [workspaceId, setWorkspaceId] = useState<string | undefined>(undefined)
  const [roleSlug, setRoleSlug] = useState<string | undefined>(undefined)
  const [projectId, setProjectId] = useState<string | undefined>(undefined)

  const workspaces = useWorkspaces(0)
  const roles = useWorkspaceRoles(workspaceId)
  const projects = usePlatformProjects({ workspaceId }, 0, 'createdAt,desc')
  const approve = useApproveAccount()

  const reset = () => {
    setWorkspaceId(undefined)
    setRoleSlug(undefined)
    setProjectId(undefined)
    approve.reset()
  }

  const close = (next: boolean) => {
    if (!next) reset()
    onOpenChange(next)
  }

  const submit = () => {
    if (!account || !workspaceId || !roleSlug) return
    approve.mutate(
      { workspaceId, userId: account.id, body: { roleSlug, projectId } },
      { onSuccess: () => close(false) },
    )
  }

  // A workspace role only means something inside its own workspace, so changing
  // the workspace clears both choices that hang off it rather than carrying a
  // role or a project the new one does not have.
  const chooseWorkspace = (next: string) => {
    setWorkspaceId(next)
    setRoleSlug(undefined)
    setProjectId(undefined)
  }

  const workspaceOptions = workspaces.data?.content.slice(0, WORKSPACE_OPTION_SIZE) ?? []
  const projectOptions = projects.data?.content ?? []

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Approve {account ? `${account.firstName} ${account.lastName}` : 'account'}</DialogTitle>
          <DialogDescription>
            {account?.email} joins the workspace you choose and can sign in to it straight away.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <FormError error={approve.error} title="Could not approve this account" />

          <div className="space-y-2">
            <Label htmlFor="approve-workspace">Workspace</Label>
            <Select value={workspaceId} onValueChange={chooseWorkspace}>
              <SelectTrigger id="approve-workspace" className="w-full">
                <SelectValue placeholder="Choose a workspace" />
              </SelectTrigger>
              <SelectContent>
                {workspaceOptions.map((workspace) => (
                  <SelectItem key={workspace.id} value={workspace.id}>
                    {workspace.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="approve-role">Role</Label>
            <Select value={roleSlug} onValueChange={setRoleSlug} disabled={!workspaceId}>
              <SelectTrigger id="approve-role" className="w-full">
                <SelectValue placeholder={workspaceId ? 'Choose a role' : 'Choose a workspace first'} />
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
            <Label htmlFor="approve-project">Project (optional)</Label>
            <Select value={projectId} onValueChange={setProjectId} disabled={!workspaceId}>
              <SelectTrigger id="approve-project" className="w-full">
                <SelectValue placeholder={workspaceId ? 'No project for now' : 'Choose a workspace first'} />
              </SelectTrigger>
              <SelectContent>
                {projectOptions.map((project) => (
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
          <Button onClick={submit} disabled={!workspaceId || !roleSlug || approve.isPending}>
            {approve.isPending ? 'Approving…' : 'Approve'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
