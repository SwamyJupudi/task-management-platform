import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { Field } from '@/components/common/field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { FormError } from '@/features/auth'
import { applyFieldErrors } from '@/lib/form'

import { AccountShell } from '../components/account-shell'
import { useCurrentAccount, useUpdateProfile } from '../hooks'
import { profileSchema, type ProfileValues } from '../schemas'

/**
 * Your own name.
 *
 * The only thing on this screen that can be changed. The address is shown and
 * is not editable, because the backend has no field for it: an address is the
 * account's identity, changing it would need re-verification and a decision
 * about live sessions, and none of that exists. Saying so beats leaving
 * somebody to hunt for a control that is not there.
 *
 * The form is seeded from the session store rather than from a request. That is
 * where the header and the account menu read the same values from, so the three
 * cannot disagree, and there is nothing to load before the form can be shown.
 */
export function AccountProfilePage() {
  const user = useCurrentAccount()
  const updateProfile = useUpdateProfile()

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: user?.firstName ?? '', lastName: user?.lastName ?? '' },
  })

  // The store settles after the session bootstrap, which can land after this
  // form has mounted. Seeding again when it does is what stops the fields
  // showing empty on a hard reload.
  useEffect(() => {
    if (user) reset({ firstName: user.firstName, lastName: user.lastName })
  }, [user, reset])

  const onSubmit = handleSubmit(async (values) => {
    try {
      const updated = await updateProfile.mutateAsync(values)
      toast.success('Your profile was updated.')
      reset({ firstName: updated.firstName, lastName: updated.lastName })
    } catch (error) {
      applyFieldErrors(error, setError, ['firstName', 'lastName'])
    }
  })

  if (!user) return null

  return (
    <AccountShell title="Your account" description="How you appear to everybody you work with">
      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
          <CardDescription>
            Your name appears on every task you are assigned, every comment you write and every
            history entry you cause.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <FormError error={updateProfile.error} title="Could not save your profile" />

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                label="First name"
                autoComplete="given-name"
                error={errors.firstName}
                {...register('firstName')}
              />
              <Field
                label="Last name"
                autoComplete="family-name"
                error={errors.lastName}
                {...register('lastName')}
              />
            </div>

            <Field
              label="Email address"
              value={user.email}
              readOnly
              disabled
              hint="Your address identifies your account and cannot be changed here."
              onChange={() => undefined}
            />

            <div className="flex justify-end">
              <Button type="submit" disabled={isSubmitting || !isDirty}>
                {isSubmitting ? 'Saving…' : 'Save changes'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </AccountShell>
  )
}
