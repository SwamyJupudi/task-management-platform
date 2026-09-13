import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { Field } from '@/components/common/field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { FormError } from '@/features/auth'
import { useWorkspaceRoles } from '@/features/people'
import { applyFieldErrors } from '@/lib/form'

import { useWorkspaceMutations, useWorkspacePermissions } from '../hooks'
import { workspaceSettingsSchema, type WorkspaceSettingsValues } from '../schemas'
import { supportedTimezones } from '../timezones'
import type { UpdateWorkspaceInput, Workspace } from '../types'

/**
 * The four settings a workspace has.
 *
 * **Only dirty fields are sent**, and that is the contract rather than an
 * optimisation. `PATCH /workspaces/{id}` checks each field against null before
 * touching it, so a body carrying every value would overwrite whatever somebody
 * else changed between this form loading and being saved. React Hook Form
 * already tracks which fields moved; this reads that rather than diffing.
 *
 * The description is the exception worth knowing: blank clears it, because
 * blank and absent are different things to this endpoint. Emptying the box and
 * saving therefore removes the description rather than leaving it alone.
 *
 * The slug is shown and cannot be edited. The backend has no field for it — it
 * appears in links that have already been shared — so the form says so rather
 * than leaving somebody hunting for a control that does not exist.
 */
export function GeneralSettingsForm({ workspace }: { workspace: Workspace }) {
  const { canUpdate } = useWorkspacePermissions()
  const { update } = useWorkspaceMutations()
  const roles = useWorkspaceRoles()

  const archived = workspace.status === 'ARCHIVED'
  const editable = canUpdate && !archived

  const zones = useMemo(() => supportedTimezones(workspace.timezone), [workspace.timezone])

  const defaults = useMemo<WorkspaceSettingsValues>(
    () => ({
      name: workspace.name,
      description: workspace.description ?? '',
      timezone: workspace.timezone,
      defaultRoleSlug: workspace.defaultRoleSlug,
    }),
    [workspace],
  )

  const {
    control,
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, dirtyFields, isDirty },
  } = useForm<WorkspaceSettingsValues>({
    resolver: zodResolver(workspaceSettingsSchema),
    defaultValues: defaults,
  })

  // The workspace can change under the form — somebody else's save, or this
  // one's own response. Re-seeding on the record rather than on mount is what
  // keeps the fields showing what is currently true.
  useEffect(() => reset(defaults), [defaults, reset])

  const onSubmit = handleSubmit(async (values) => {
    const body: UpdateWorkspaceInput = {}
    if (dirtyFields.name) body.name = values.name
    if (dirtyFields.description) body.description = values.description
    if (dirtyFields.timezone) body.timezone = values.timezone
    if (dirtyFields.defaultRoleSlug) body.defaultRoleSlug = values.defaultRoleSlug

    if (Object.keys(body).length === 0) return

    try {
      await update.mutateAsync(body)
      toast.success('Workspace settings saved.')
    } catch (error) {
      applyFieldErrors(error, setError, ['name', 'description', 'timezone', 'defaultRoleSlug'])
    }
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>General</CardTitle>
        <CardDescription>
          The name everybody sees, and the settings every date and every invitation in this
          workspace depends on.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <FormError error={update.error} title="Could not save these settings" />

          <Field label="Name" error={errors.name} disabled={!editable} {...register('name')} />

          <Field
            label="Address"
            value={workspace.slug}
            readOnly
            disabled
            hint="This appears in links that may already have been shared, so it cannot be changed."
            onChange={() => undefined}
          />

          <div className="space-y-1.5">
            <Label htmlFor="workspace-description">Description</Label>
            <Textarea
              id="workspace-description"
              rows={3}
              disabled={!editable}
              aria-invalid={errors.description ? true : undefined}
              {...register('description')}
            />
            {errors.description ? (
              <p className="text-sm text-destructive" role="alert">
                {errors.description.message}
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">
                Optional. Clearing the box removes it.
              </p>
            )}
          </div>

          <Controller
            control={control}
            name="timezone"
            render={({ field }) => (
              <div className="space-y-1.5">
                <Label htmlFor="workspace-timezone">Time zone</Label>
                <Select value={field.value} onValueChange={field.onChange} disabled={!editable}>
                  <SelectTrigger id="workspace-timezone" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="max-h-72">
                    {zones.map((zone) => (
                      <SelectItem key={zone} value={zone}>
                        {zone}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.timezone ? (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.timezone.message}
                  </p>
                ) : (
                  <p className="text-xs text-muted-foreground">
                    Every report window and overdue date in this workspace is read in this zone.
                  </p>
                )}
              </div>
            )}
          />

          {/* Reading the roles costs `role:read`, which an employee does not
              hold. Without them there is nothing to choose from, so the field
              is dropped rather than shown empty; the saved value is untouched
              because an omitted field is left alone. */}
          {roles.data && roles.data.length > 0 ? (
            <Controller
              control={control}
              name="defaultRoleSlug"
              render={({ field }) => (
                <div className="space-y-1.5">
                  <Label htmlFor="workspace-default-role">Default role</Label>
                  <Select value={field.value} onValueChange={field.onChange} disabled={!editable}>
                    <SelectTrigger id="workspace-default-role" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {roles.data.map((role) => (
                        <SelectItem key={role.slug} value={role.slug}>
                          {role.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {errors.defaultRoleSlug ? (
                    <p className="text-sm text-destructive" role="alert">
                      {errors.defaultRoleSlug.message}
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      What somebody invited without a named role joins as.
                    </p>
                  )}
                </div>
              )}
            />
          ) : null}

          {editable ? (
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={!isDirty || isSubmitting}
                onClick={() => reset(defaults)}
              >
                Reset
              </Button>
              <Button type="submit" disabled={!isDirty || isSubmitting}>
                {isSubmitting ? 'Saving…' : 'Save changes'}
              </Button>
            </div>
          ) : null}
        </form>
      </CardContent>
    </Card>
  )
}
