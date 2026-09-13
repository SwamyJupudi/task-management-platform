import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { CircleCheckIcon } from 'lucide-react'

import { paths } from '@/app/routes/paths'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { PasswordField } from '../components/text-field'
import { applyFieldErrors, useResetPassword } from '../hooks'
import { resetPasswordSchema, type ResetPasswordValues } from '../schemas'

/**
 * Choose a new password from a reset link.
 *
 * The token arrives in the query string and is posted with the new password.
 * A reset token lives one hour, so an expired one is a normal outcome rather
 * than an error worth alarming anybody about; the screen offers the way to get
 * a fresh one.
 *
 * The backend revokes every session on success, including any this browser
 * held, because somebody resetting a password may be recovering a compromised
 * account. There is therefore nothing to sign in to afterwards, and the screen
 * sends the user to the sign-in form rather than into the application.
 */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const reset = useResetPassword()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit(async ({ newPassword }) => {
    if (!token) return
    try {
      await reset.mutateAsync({ token, newPassword })
    } catch (error) {
      applyFieldErrors(error, setError, ['newPassword'])
    }
  })

  if (reset.isSuccess) {
    return (
      <AuthCard title="Password changed">
        <Alert variant="success">
          <CircleCheckIcon aria-hidden="true" />
          <AlertTitle>Your password has been set</AlertTitle>
          <AlertDescription>
            <p>
              Every other session was signed out as a precaution. Sign in again with your new
              password.
            </p>
          </AlertDescription>
        </Alert>
        <Link
          to={paths.auth.login}
          className="flex h-9 w-full items-center justify-center rounded-lg bg-primary text-sm font-medium text-primary-foreground hover:bg-primary/80"
        >
          Continue to sign in
        </Link>
      </AuthCard>
    )
  }

  if (!token) {
    return (
      <AuthCard
        title="Choose a new password"
        description="This page needs the link from your reset email."
        footer={
          <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
            Back to sign in
          </Link>
        }
      >
        <p className="text-sm text-muted-foreground">
          The link may have been copied incompletely.{' '}
          <Link
            to={paths.auth.forgotPassword}
            className="font-medium text-foreground hover:underline"
          >
            Request a new one
          </Link>
          .
        </p>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Choose a new password"
      description="Signing in again afterwards will use this password."
      footer={
        <Link
          to={paths.auth.forgotPassword}
          className="font-medium text-foreground hover:underline"
        >
          Request a new link
        </Link>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError error={reset.error} title="Could not set your password" />

        <PasswordField
          label="New password"
          autoComplete="new-password"
          autoFocus
          hint="At least 8 characters."
          error={errors.newPassword}
          {...register('newPassword')}
        />

        <PasswordField
          label="Confirm new password"
          autoComplete="new-password"
          error={errors.confirmPassword}
          {...register('confirmPassword')}
        />

        <SubmitButton pending={isSubmitting || reset.isPending} pendingLabel="Saving…">
          Set new password
        </SubmitButton>
      </form>
    </AuthCard>
  )
}
