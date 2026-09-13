import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { Field } from '@/components/common/field'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  FormError,
  changePasswordSchema,
  useChangePassword,
  type ChangePasswordValues,
} from '@/features/auth'
import { applyFieldErrors } from '@/lib/form'

import { AccountShell } from '../components/account-shell'

/**
 * Changing your own password.
 *
 * The current password is asked for even though a session already exists, which
 * is the backend's rule and worth understanding rather than working around: an
 * unattended browser is exactly the case it defends against.
 *
 * **Every other session ends.** The endpoint revokes them all and immediately
 * issues a fresh pair for this browser, so the person changing their password
 * stays signed in here and is signed out everywhere else. That is stated before
 * the form rather than discovered afterwards, because it is the consequence
 * somebody most needs to know about — especially if the reason they are here is
 * that they think somebody else has their password.
 */
export function AccountPasswordPage() {
  const changePassword = useChangePassword()

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      await changePassword.mutateAsync({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      toast.success('Your password was changed. Your other sessions have been ended.')
      reset({ currentPassword: '', newPassword: '', confirmPassword: '' })
    } catch (error) {
      // The backend names `currentPassword` when it is wrong; anything it does
      // not name falls through to the banner.
      applyFieldErrors(error, setError, ['currentPassword', 'newPassword'])
    }
  })

  return (
    <AccountShell title="Your account" description="How you appear to everybody you work with">
      <Card>
        <CardHeader>
          <CardTitle>Password</CardTitle>
          <CardDescription>
            Use at least eight characters. Anything you can remember and nobody can guess.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <Alert className="mb-4">
            <AlertTitle>This ends your other sessions</AlertTitle>
            <AlertDescription>
              <p>
                Changing your password signs you out everywhere else. This browser stays signed in.
              </p>
            </AlertDescription>
          </Alert>

          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <FormError error={changePassword.error} title="Could not change your password" />

            <Field
              label="Current password"
              type="password"
              autoComplete="current-password"
              error={errors.currentPassword}
              {...register('currentPassword')}
            />
            <Field
              label="New password"
              type="password"
              autoComplete="new-password"
              error={errors.newPassword}
              {...register('newPassword')}
            />
            <Field
              label="Confirm new password"
              type="password"
              autoComplete="new-password"
              error={errors.confirmPassword}
              {...register('confirmPassword')}
            />

            <div className="flex justify-end">
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Changing…' : 'Change password'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </AccountShell>
  )
}
