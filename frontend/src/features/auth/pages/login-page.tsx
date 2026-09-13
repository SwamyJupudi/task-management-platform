import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { PasswordField, TextField } from '../components/text-field'
import { applyFieldErrors, useLogin, useResendVerification } from '../hooks'
import { loginSchema, type LoginValues } from '../schemas'

/**
 * Sign in.
 *
 * Nothing here navigates on success. The mutation puts the session in the
 * store and `RequireAnonymous` moves the user on, to the page they originally
 * asked for when there was one.
 *
 * The one branch worth special handling is `EMAIL_NOT_VERIFIED`. The backend
 * returns it with a correct password, so the user has done nothing wrong and
 * telling them to check their email is useless if the message has expired.
 * That case gets a button that sends another one.
 */
export function LoginPage() {
  const login = useLogin()
  const resend = useResendVerification()

  const {
    register,
    handleSubmit,
    getValues,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const unverified = login.error instanceof ApiError && login.error.code === 'EMAIL_NOT_VERIFIED'

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login.mutateAsync(values)
    } catch (error) {
      applyFieldErrors(error, setError, ['email', 'password'])
    }
  })

  return (
    <AuthCard
      title="Sign in"
      description="Use the email address your account was created with."
      footer={
        <>
          New here?{' '}
          <Link to={paths.auth.register} className="font-medium text-foreground hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError error={login.error} title="Could not sign you in" />

        {unverified ? (
          resend.isSuccess ? (
            <p className="text-sm text-muted-foreground">
              A new verification message is on its way to that address.
            </p>
          ) : (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full"
              disabled={resend.isPending}
              onClick={() => resend.mutate(getValues('email'))}
            >
              {resend.isPending ? 'Sending…' : 'Send another verification email'}
            </Button>
          )
        ) : null}

        <TextField
          label="Email address"
          type="email"
          autoComplete="username"
          autoFocus
          placeholder="person@example.com"
          error={errors.email}
          {...register('email')}
        />

        <PasswordField
          label="Password"
          autoComplete="current-password"
          error={errors.password}
          action={
            <Link
              to={paths.auth.forgotPassword}
              className="text-xs text-muted-foreground hover:underline"
            >
              Forgot your password?
            </Link>
          }
          {...register('password')}
        />

        <SubmitButton pending={isSubmitting || login.isPending} pendingLabel="Signing in…">
          Sign in
        </SubmitButton>
      </form>
    </AuthCard>
  )
}
