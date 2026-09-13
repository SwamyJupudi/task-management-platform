import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { Field } from '@/components/common/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
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
import { toUserMessage } from '@/lib/api'
import { applyFieldErrors } from '@/lib/form'

import { usePeopleMutations, useWorkspaceRoles } from '../hooks'
import { inviteSchema, type InviteValues } from '../schemas'

/**
 * Invites an address to the workspace.
 *
 * Sending to an address that already has an outstanding invitation supersedes
 * it rather than failing, which is the platform's only way to send another —
 * there is no resend endpoint. The dialog says so, because "invite" and "send
 * it again" being the same button is otherwise surprising.
 *
 * The role is optional: omitted means the workspace's default, which the
 * backend applies. Without `role:read` there is nothing to populate the picker
 * with, so the field is dropped and the default is used.
 */

/** A Select item cannot hold an empty string, so "the default" needs a token. */
const DEFAULT_ROLE = '__default__'

export function InviteDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { sendInvite } = usePeopleMutations()
  const roles = useWorkspaceRoles()

  const {
    control,
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: '', roleSlug: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      const invitation = await sendInvite.mutateAsync({
        email: values.email,
        ...(values.roleSlug !== '' ? { roleSlug: values.roleSlug } : {}),
      })
      toast.success(`Invitation sent to ${invitation.email}.`)
      reset({ email: '', roleSlug: '' })
      onOpenChange(false)
    } catch (error) {
      applyFieldErrors(error, setError, ['email', 'roleSlug'])
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Invite somebody</DialogTitle>
          <DialogDescription>
            They receive a link by email. Inviting an address that already has an outstanding
            invitation replaces it with a fresh one.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {sendInvite.isError ? (
            <Alert variant="destructive">
              <AlertDescription>{toUserMessage(sendInvite.error)}</AlertDescription>
            </Alert>
          ) : null}

          <Field
            label="Email address"
            type="email"
            placeholder="person@example.com"
            error={errors.email}
            {...register('email')}
          />

          {roles.data && roles.data.length > 0 ? (
            <Controller
              control={control}
              name="roleSlug"
              render={({ field }) => (
                <div className="space-y-1.5">
                  <Label htmlFor="invite-role">Role</Label>
                  <Select
                    value={field.value === '' ? DEFAULT_ROLE : field.value}
                    onValueChange={(next) => field.onChange(next === DEFAULT_ROLE ? '' : next)}
                  >
                    <SelectTrigger id="invite-role" className="h-9 w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={DEFAULT_ROLE}>The workspace default</SelectItem>
                      {roles.data.map((role) => (
                        <SelectItem key={role.slug} value={role.slug}>
                          {role.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
            />
          ) : (
            <p className="text-xs text-muted-foreground">
              They join with the workspace's default role. Choosing a different one needs the
              role:read permission.
            </p>
          )}

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Sending…' : 'Send invitation'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
