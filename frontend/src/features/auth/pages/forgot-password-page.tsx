import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { MailCheckIcon } from 'lucide-react'

import { paths } from '@/app/routes/paths'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { TextField } from '../components/text-field'
import { applyFieldErrors, useForgotPassword } from '../hooks'
import { emailOnlySchema, type EmailOnlyValues } from '../schemas'

/**
 * Ask for a password reset message.
 *
 * The endpoint answers 202 whether or not the address is registered, so that
 * the response cannot be used to test which addresses have accounts. The
 * confirmation below is worded to preserve that: it says what will happen *if*
 * the address is known, and never that it is.
 */
export function ForgotPasswordPage() {
  const forgot = useForgotPassword()

  const {
    register,
    handleSubmit,
    getValues,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<EmailOnlyValues>({
    resolver: zodResolver(emailOnlySchema),
    defaultValues: { email: '' },
  })

  const onSubmit = handleSubmit(async ({ email }) => {
    try {
      await forgot.mutateAsync(email)
    } catch (error) {
      applyFieldErrors(error, setError, ['email'])
    }
  })

  if (forgot.isSuccess) {
    return (
      <AuthCard
        title="Check your email"
        footer={
          <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
            Back to sign in
          </Link>
        }
      >
        <Alert variant="success">
          <MailCheckIcon aria-hidden="true" />
          <AlertTitle>Message sent</AlertTitle>
          <AlertDescription>
            <p>
              If <span className="font-medium text-foreground">{getValues('email')}</span> has an
              account, a link to choose a new password is on its way. It expires in an hour.
            </p>
          </AlertDescription>
        </Alert>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Forgot your password"
      description="Enter your address and we will send you a link to choose a new one."
      footer={
        <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
          Back to sign in
        </Link>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormError error={forgot.error} title="Could not send the message" />

        <TextField
          label="Email address"
          type="email"
          autoComplete="username"
          autoFocus
          placeholder="person@example.com"
          error={errors.email}
          {...register('email')}
        />

        <SubmitButton pending={isSubmitting || forgot.isPending} pendingLabel="Sending…">
          Send reset link
        </SubmitButton>
      </form>
    </AuthCard>
  )
}
