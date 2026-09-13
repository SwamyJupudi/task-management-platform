import { ShieldIcon } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

import { AdminShell } from '../components/admin-shell'
import { RoleEditor } from '../components/role-editor'
import { WorkspacePicker } from '../components/workspace-picker'
import {
  useAdminPermissions,
  usePermissionCatalog,
  useWorkspaceOptions,
  useWorkspaceRoles,
} from '../hooks'

/**
 * Roles and permissions, one workspace at a time.
 *
 * **Roles are per workspace**, which is why this screen starts with a picker
 * rather than showing one global list. Each workspace gets its own copy of the
 * seeded roles when it is created, so `ADMIN` in one workspace and `ADMIN` in
 * another are two different rows that can grant different things.
 *
 * The platform role is deliberately unreachable from here, and that is the
 * schema's doing rather than a check on this side: roles are addressed by
 * `(workspace, slug)` and a platform role has no workspace, so it matches no
 * such pair. The same property is what stops a workspace member being assigned
 * it. Granting the platform role is on the accounts screen, where it belongs.
 *
 * Reading needs `role:read` and the catalog needs `permission:read`; editing
 * needs `role:manage`. All three are asked platform-wide, because this panel is
 * outside any workspace — a workspace administrator holds all three inside
 * their own workspace and reaches this screen through neither.
 */
export function RolesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const permissions = useAdminPermissions()

  const workspaces = useWorkspaceOptions()
  const catalog = usePermissionCatalog()

  const workspaceId = searchParams.get('workspaceId') ?? undefined
  const roles = useWorkspaceRoles(workspaceId)

  const workspace = workspaces.data?.find((candidate) => candidate.id === workspaceId)
  const roleSlug = searchParams.get('role') ?? undefined
  const selectedRole =
    roles.data?.find((role) => role.slug === roleSlug) ?? roles.data?.[0] ?? undefined

  const setParam = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(searchParams)
    if (value === undefined) next.delete(key)
    else next.set(key, value)
    // A role slug belongs to the workspace it came from; carrying one across
    // would select a role of a workspace that may not have it.
    if (key === 'workspaceId') next.delete('role')
    setSearchParams(next, { replace: true })
  }

  if (!permissions.canReadRoles || !permissions.canReadPermissionCatalog) {
    return (
      <AdminShell title="Roles and permissions">
        <EmptyState
          icon={ShieldIcon}
          title="You cannot edit roles here"
          description="This screen needs role:read and permission:read on the platform. A workspace grant covers that workspace's own settings, not this panel."
        />
      </AdminShell>
    )
  }

  return (
    <AdminShell
      title="Roles and permissions"
      description="What each role of a workspace may do"
      actions={
        workspaces.data ? (
          <WorkspacePicker
            id="roles-workspace"
            workspaces={workspaces.data}
            value={workspaceId}
            onChange={(next) => setParam('workspaceId', next)}
          />
        ) : undefined
      }
    >
      {workspaces.isError ? (
        <ErrorState error={workspaces.error} onRetry={() => void workspaces.refetch()} />
      ) : catalog.isError ? (
        <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
      ) : workspaces.isPending || catalog.isPending ? (
        <LoadingState label="Loading workspaces and the permission catalog" />
      ) : workspaceId === undefined || workspace === undefined ? (
        <EmptyState
          icon={ShieldIcon}
          title="Choose a workspace"
          description="Every workspace has its own copy of the roles, so they are edited one workspace at a time."
        />
      ) : roles.isError ? (
        <ErrorState error={roles.error} onRetry={() => void roles.refetch()} />
      ) : roles.isPending ? (
        <LoadingState label={`Loading the roles of ${workspace.name}`} />
      ) : roles.data && roles.data.length === 0 ? (
        <EmptyState icon={ShieldIcon} title={`${workspace.name} has no roles`} />
      ) : roles.data && selectedRole ? (
        <Tabs
          value={selectedRole.slug}
          onValueChange={(next) => setParam('role', next)}
          className="space-y-4"
        >
          <TabsList className="flex-wrap">
            {roles.data.map((role) => (
              <TabsTrigger key={role.slug} value={role.slug}>
                {role.name}
              </TabsTrigger>
            ))}
          </TabsList>

          {roles.data.map((role) => (
            <TabsContent key={role.slug} value={role.slug}>
              {/* Keyed by workspace and role so switching either remounts the
                  editor with a fresh selection rather than carrying the
                  previous role's ticks across. */}
              <RoleEditor
                key={`${workspace.id}-${role.slug}`}
                workspace={workspace}
                role={role}
                catalog={catalog.data ?? []}
              />
            </TabsContent>
          ))}
        </Tabs>
      ) : null}
    </AdminShell>
  )
}
