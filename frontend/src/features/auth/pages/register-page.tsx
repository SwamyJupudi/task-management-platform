import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { HourglassIcon } from 'lucide-react'

import { paths } from '@/app/routes/paths'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { PasswordField, TextField } from '../components/text-field'
import { applyFieldErrors, useRegister } from '../hooks'
import { registerSchema, type RegisterValues } from '../schemas'

/**
 * Create an account.
 *
 * Registration issues no tokens and sends no mail. The backend creates the
 * person in the approval queue and stops, so the screen switches to a
 * confirmation rather than signing anybody in.
 *
 * What it says matters: nothing has been sent, and telling somebody to check
 * their inbox would send them looking for a message that does not exist and
 * could not let them in anyway. An administrator has to admit them, and that is
 * what this says.
 *
 * `confirmPassword` never leaves the browser: it is a guard against a typo on
 * the one form whose value is never echoed back, and it is dropped here.
 */
export function RegisterPage() {
  const createAccount = useRegister()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { firstName: '', lastName: '', email: '', password: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit(async ({ confirmPassword: _confirmPassword, ...body }) => {
    try {
      await createAccount.mutateAsync(body)
    } catch (error) {
      applyFieldErrors(error, setError, ['firstName', 'lastName', 'email', 'password'])
    }
  })

  if (createAccount.isSuccess) {
    const address = createAccount.data.email
    return (
      <AuthCard
        title="Your account is waiting for approval"
        footer={
          <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
            Back to sign in
          </Link>
        }
      >
        <Alert variant="success">
          <HourglassIcon aria-hidden="true" />
          <AlertTitle>Account created</AlertTitle>
          <AlertDescription>
            <p>
              <span className="font-medium text-foreground">{address}</span> is registered. An
              administrator has to approve it and choose which workspace you join. You can sign in
              now to check, and your work will appear once they do.
            </p>
          </AlertDescription>
        </Alert>

        <Button asChild className="w-full">
          <Link to={paths.auth.login}>Sign in</Link>
        </Button>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Create an account"
      description="An administrator approves new accounts and chooses the workspace you join."
      footer={
        <>
          Already have an account?{' '}
          <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError error={createAccount.error} title="Could not create the account" />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField
            label="First name"
            autoComplete="given-name"
            autoFocus
            error={errors.firstName}
            {...register('firstName')}
          />
          <TextField
            label="Last name"
            autoComplete="family-name"
            error={errors.lastName}
            {...register('lastName')}
          />
        </div>

        <TextField
          label="Email address"
          type="email"
          autoComplete="username"
          placeholder="person@example.com"
          error={errors.email}
          {...register('email')}
        />

        <PasswordField
          label="Password"
          autoComplete="new-password"
          hint="At least 8 characters."
          error={errors.password}
          {...register('password')}
        />

        <PasswordField
          label="Confirm password"
          autoComplete="new-password"
          error={errors.confirmPassword}
          {...register('confirmPassword')}
        />

        <SubmitButton
          pending={isSubmitting || createAccount.isPending}
          pendingLabel="Creating your account…"
        >
          Create account
        </SubmitButton>
      </form>
    </AuthCard>
  )
}
