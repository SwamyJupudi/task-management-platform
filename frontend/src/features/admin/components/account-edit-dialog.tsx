import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
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
import { toUserMessage } from '@/lib/api'
import { applyFieldErrors } from '@/lib/form'

import { useAccountMutations } from '../hooks'
import { accountProfileSchema, type AccountProfileValues } from '../schemas'
import type { PlatformAccount } from '../types'

/**
 * Editing somebody else's name.
 *
 * Names only. The address is absent because the backend has no field for it
 * here, and the dialog says why rather than leaving an administrator hunting
 * for a control that does not exist.
 *
 * This is a different endpoint from the one a person uses on themselves, so
 * that renaming yourself is never recorded as an administrative action. The
 * audit trail is the reason the two are separate.
 */
export function AccountEditDialog({
  account,
  open,
  onOpenChange,
}: {
  account: PlatformAccount
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { updateProfile } = useAccountMutations()

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AccountProfileValues>({
    resolver: zodResolver(accountProfileSchema),
    defaultValues: { firstName: account.firstName, lastName: account.lastName },
  })

  // The dialog is mounted per row, but the row's names can change under it
  // after a refetch. Resetting on open means the form always opens on what is
  // currently true rather than on what was true when it first mounted.
  useEffect(() => {
    if (open) reset({ firstName: account.firstName, lastName: account.lastName })
  }, [open, account.firstName, account.lastName, reset])

  const onSubmit = handleSubmit(async (values) => {
    try {
      const updated = await updateProfile.mutateAsync({ userId: account.id, body: values })
      toast.success(`Renamed to ${updated.firstName} ${updated.lastName}.`)
      onOpenChange(false)
    } catch (error) {
      applyFieldErrors(error, setError, ['firstName', 'lastName'])
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Edit {account.email}</DialogTitle>
          <DialogDescription>
            The name this account is shown under everywhere. Its email address is its identity and
            cannot be changed here.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {updateProfile.isError ? (
            <Alert variant="destructive">
              <AlertDescription>{toUserMessage(updateProfile.error)}</AlertDescription>
            </Alert>
          ) : null}

          <Field label="First name" error={errors.firstName} {...register('firstName')} />
          <Field label="Last name" error={errors.lastName} {...register('lastName')} />

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={isSubmitting}
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
