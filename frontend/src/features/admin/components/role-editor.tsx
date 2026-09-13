import { useMemo, useState } from 'react'
import { toast } from 'sonner'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { toUserMessage } from '@/lib/api'

import { humanise } from '../constants'
import { useAdminPermissions, useRoleMutations } from '../hooks'
import type { PermissionEntry, Role, WorkspaceSummary } from '../types'

/**
 * Editing what one role grants.
 *
 * **Replacement, not a delta**, because that is what the endpoint takes: the
 * complete set the role should hold afterwards. A body of additions and
 * removals would need the client to know the current state to compute it, and
 * two administrators editing the same role at once would merge into a set
 * neither of them chose.
 *
 * The catalog is grouped by resource, which is the shape of the code itself —
 * `task:read` is the `read` action on the `task` resource — so the grouping is
 * read from the data rather than being a list of headings kept in step by hand.
 * A resource added by a later migration appears on its own without any change
 * here.
 *
 * Every code is offered, including the platform-only ones such as
 * `admin:read_system`. That is deliberate rather than an oversight: the backend
 * accepts any code in the catalog, and a workspace role holding a platform code
 * still gates nothing, because `@perm.onPlatform` never consults workspace
 * membership. Hiding them would be this screen inventing a rule the platform
 * does not have — so they are shown, and marked, and the note says what they
 * do and do not do.
 *
 * Two refusals belong to the backend and are surfaced rather than pre-empted:
 * an archived workspace is frozen against every change, and a caller may not
 * remove their own ability to edit roles. The first is known from the workspace
 * row, so the editor says so up front and disables saving; the second depends
 * on how the caller holds the grant, which this screen cannot see, so it is
 * left to the 409.
 */

/** Codes no workspace role can act on, because their endpoints are platform-gated. */
const PLATFORM_ONLY_RESOURCES = new Set(['admin', 'platform_role'])

export function RoleEditor({
  workspace,
  role,
  catalog,
}: {
  workspace: WorkspaceSummary
  role: Role
  catalog: PermissionEntry[]
}) {
  const { canManageRoles } = useAdminPermissions()
  const { replacePermissions } = useRoleMutations()

  const [selected, setSelected] = useState<Set<string>>(() => new Set(role.permissions))

  // The role can change under the editor after a save or a refetch. Reconciled
  // during render against the last set seen, rather than in an effect that
  // would render once with the stale selection and then again to correct it.
  const [lastPermissions, setLastPermissions] = useState(role.permissions)
  if (lastPermissions !== role.permissions) {
    setLastPermissions(role.permissions)
    setSelected(new Set(role.permissions))
  }

  const groups = useMemo(() => {
    const byResource = new Map<string, PermissionEntry[]>()
    for (const entry of catalog) {
      const bucket = byResource.get(entry.resource)
      if (bucket) bucket.push(entry)
      else byResource.set(entry.resource, [entry])
    }
    return [...byResource.entries()]
      .map(([resource, entries]) => ({
        resource,
        entries: [...entries].sort((a, b) => a.action.localeCompare(b.action)),
      }))
      .sort((a, b) => a.resource.localeCompare(b.resource))
  }, [catalog])

  const current = useMemo(() => new Set(role.permissions), [role.permissions])
  const added = [...selected].filter((code) => !current.has(code))
  const removed = [...current].filter((code) => !selected.has(code))
  const dirty = added.length > 0 || removed.length > 0

  const archived = workspace.status === 'ARCHIVED'
  const editable = canManageRoles && !archived

  const toggle = (code: string) => {
    setSelected((previous) => {
      const next = new Set(previous)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  const save = async () => {
    try {
      const updated = await replacePermissions.mutateAsync({
        workspaceId: workspace.id,
        roleSlug: role.slug,
        permissions: [...selected],
      })
      toast.success(
        `${updated.name} now grants ${updated.permissions.length.toLocaleString()} permissions.`,
      )
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h3 className="flex flex-wrap items-center gap-2 text-sm font-medium">
            {role.name}
            <span className="font-mono text-xs text-muted-foreground">{role.slug}</span>
            {role.system ? <Badge variant="outline">Seeded</Badge> : null}
          </h3>
          <p className="text-xs text-muted-foreground">
            {selected.size.toLocaleString()} of {catalog.length.toLocaleString()} permissions
            {dirty
              ? ` · ${added.length.toLocaleString()} to add, ${removed.length.toLocaleString()} to remove`
              : ''}
          </p>
        </div>

        {editable ? (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!dirty || replacePermissions.isPending}
              onClick={() => setSelected(new Set(role.permissions))}
            >
              Reset
            </Button>
            <Button size="sm" disabled={!dirty || replacePermissions.isPending} onClick={save}>
              {replacePermissions.isPending ? 'Saving…' : 'Save changes'}
            </Button>
          </div>
        ) : null}
      </div>

      {archived ? (
        <Alert>
          <AlertDescription>
            {workspace.name} is archived, so it is frozen against every change including this one.
            Restore it from the workspace itself to edit its roles.
          </AlertDescription>
        </Alert>
      ) : null}

      {!canManageRoles ? (
        <Alert>
          <AlertDescription>
            You can read what each role grants. Changing it needs role:manage on the platform.
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-x-8 gap-y-6 sm:grid-cols-2 xl:grid-cols-3">
        {groups.map((group) => (
          <fieldset key={group.resource} className="space-y-2">
            <legend className="flex items-center gap-2 pb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              {humanise(group.resource)}
              {PLATFORM_ONLY_RESOURCES.has(group.resource) ? (
                <Badge variant="outline" className="normal-case">
                  Platform only
                </Badge>
              ) : null}
            </legend>

            <ul className="space-y-2">
              {group.entries.map((entry) => {
                const id = `perm-${role.slug}-${entry.code}`
                return (
                  <li key={entry.code} className="flex items-start gap-2">
                    <Checkbox
                      id={id}
                      checked={selected.has(entry.code)}
                      disabled={!editable || replacePermissions.isPending}
                      onCheckedChange={() => toggle(entry.code)}
                      className="mt-0.5"
                    />
                    <Label htmlFor={id} className="flex-1 cursor-pointer font-normal">
                      <span className="block font-mono text-xs">{entry.code}</span>
                      <span className="block text-xs text-muted-foreground">
                        {entry.description}
                      </span>
                    </Label>
                  </li>
                )
              })}
            </ul>
          </fieldset>
        ))}
      </div>

      <p className="text-xs text-muted-foreground">
        A workspace role may hold a platform-only code, and it grants nothing: those endpoints ask
        for a platform role and never consult workspace membership.
      </p>
    </div>
  )
}
