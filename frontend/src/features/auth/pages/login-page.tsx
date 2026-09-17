import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { PasswordField, TextField } from '../components/text-field'
import { applyFieldErrors, useLogin } from '../hooks'
import { loginSchema, type LoginValues } from '../schemas'

/**
 * Sign in.
 *
 * Nothing here navigates on success. The mutation puts the session in the
 * store and `RequireAnonymous` moves the user on, to the page they originally
 * asked for when there was one.
 *
 * There is no unverified-address branch any more. Confirming an address is not
 * what lets somebody in -- an administrator's approval is -- so the backend no
 * longer refuses a sign-in for it, and offering to send a verification message
 * here would promise something that changes nothing. Somebody who has registered
 * and not yet been approved signs in successfully and is shown that they are
 * waiting.
 */
export function LoginPage() {
  const login = useLogin()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

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
