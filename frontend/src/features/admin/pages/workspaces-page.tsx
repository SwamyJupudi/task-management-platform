import { BuildingIcon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { PaginationBar } from '@/components/common/pagination-bar'
import { Button } from '@/components/ui/button'

import { AdminShell } from '../components/admin-shell'
import { CreateWorkspaceDialog } from '../components/create-workspace-dialog'
import { WorkspaceTable } from '../components/workspace-table'
import { useAdminPermissions, useWorkspaces } from '../hooks'

/**
 * Every workspace in the installation, and the one screen that creates one.
 *
 * Creation is platform administration — `workspace:create` is granted to no
 * workspace role — which is why it lives here rather than beside the settings
 * of a workspace somebody is already in. Deleting is the same, and for the same
 * reason: a workspace administrator cannot remove their own workspace.
 *
 * Editing a workspace's settings is deliberately not duplicated here. That is a
 * workspace-level screen with its own permission, and a second copy in this
 * panel would be two controls with different reach for one operation.
 */
export function AdminWorkspacesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { canListWorkspaces, canCreateWorkspaces } = useAdminPermissions()
  const [creating, setCreating] = useState(false)

  const rawPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) - 1 : 0

  const workspaces = useWorkspaces(page)

  const header = canCreateWorkspaces ? (
    <Button onClick={() => setCreating(true)}>
      <PlusIcon aria-hidden="true" />
      New workspace
    </Button>
  ) : undefined

  if (!canListWorkspaces) {
    return (
      <AdminShell title="Workspaces">
        <EmptyState
          icon={BuildingIcon}
          title="You cannot see every workspace"
          description="Listing the installation's workspaces needs workspace:read on the platform. A membership shows you only your own."
        />
      </AdminShell>
    )
  }

  const data = workspaces.data

  return (
    <AdminShell
      title="Workspaces"
      description="Every workspace in this installation"
      actions={header}
    >
      {workspaces.isError ? (
        <ErrorState error={workspaces.error} onRetry={() => void workspaces.refetch()} />
      ) : workspaces.isPending ? (
        <LoadingState label="Loading workspaces" />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={BuildingIcon}
          title="No workspaces yet"
          description="A workspace is where projects, teams and tasks live. Create the first one to get started."
          action={header}
        />
      ) : data ? (
        <div className="space-y-4">
          <WorkspaceTable workspaces={data.content} />
          <PaginationBar
            page={data}
            onPageChange={(next) => {
              const params = new URLSearchParams(searchParams)
              params.set('page', String(next + 1))
              setSearchParams(params, { replace: true })
            }}
          />
        </div>
      ) : null}

      <CreateWorkspaceDialog open={creating} onOpenChange={setCreating} />
    </AdminShell>
  )
}
