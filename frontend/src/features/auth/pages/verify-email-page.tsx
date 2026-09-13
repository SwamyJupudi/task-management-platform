import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { CircleCheckIcon, Loader2Icon } from 'lucide-react'

import { paths } from '@/app/routes/paths'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { TextField } from '../components/text-field'
import { useResendVerification, useVerifyEmail } from '../hooks'
import { emailOnlySchema, type EmailOnlyValues } from '../schemas'

/**
 * Confirm an email address.
 *
 * Reached from the link in the verification message, which carries the token in
 * the query string. The token is posted rather than sent as a GET parameter to
 * an API route, because the backend deliberately takes it in a body: a token in
 * a URL ends up in history, in the referrer of whatever the page loads next,
 * and in every access log in between. The page is the only place it appears,
 * and it does not leave the query string here either — but this is the last hop.
 *
 * Reachable with or without a session, since somebody may click the link in a
 * browser they have never signed in on.
 *
 * The failure path matters as much as the success one. A verification token
 * lives 24 hours, so an expired one is ordinary rather than exceptional, and the
 * screen answers it with the form that sends another.
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const verify = useVerifyEmail()
  const resend = useResendVerification()

  // Verification is a one-shot side effect of arriving, not of rendering.
  // The ref keeps StrictMode's second mount in development from spending the
  // single-use token a second time and reporting the reuse as a failure.
  const attempted = useRef(false)
  const { mutate: runVerify } = verify

  useEffect(() => {
    if (!token || attempted.current) return
    attempted.current = true
    runVerify(token)
  }, [token, runVerify])

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<EmailOnlyValues>({
    resolver: zodResolver(emailOnlySchema),
    defaultValues: { email: '' },
  })

  const onResend = handleSubmit(async ({ email }) => {
    await resend.mutateAsync(email).catch(() => undefined)
  })

  if (verify.isSuccess) {
    return (
      <AuthCard title="Email confirmed">
        <Alert variant="success">
          <CircleCheckIcon aria-hidden="true" />
          <AlertTitle>That address is verified</AlertTitle>
          <AlertDescription>
            <p>You can sign in now.</p>
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

  if (token && verify.isPending) {
    return (
      <AuthCard title="Confirming your address">
        <div className="flex items-center gap-3 py-2" role="status" aria-live="polite">
          <Loader2Icon className="size-4 animate-spin text-muted-foreground" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">This will only take a moment.</p>
        </div>
      </AuthCard>
    )
  }

  // Either the link carried no token at all, or the token was rejected. Both
  // end in the same place: ask for another message.
  return (
    <AuthCard
      title="Verify your email"
      description={
        token
          ? 'That link could not be used. Enter your address and we will send a new one.'
          : 'This page needs the link from your verification email. Enter your address to get a new one.'
      }
      footer={
        <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
          Back to sign in
        </Link>
      }
    >
      {token ? <FormError error={verify.error} title="Could not verify that link" /> : null}

      {resend.isSuccess ? (
        <Alert variant="success">
          <CircleCheckIcon aria-hidden="true" />
          <AlertTitle>Message sent</AlertTitle>
          <AlertDescription>
            <p>If that address has an account awaiting verification, a new link is on its way.</p>
          </AlertDescription>
        </Alert>
      ) : (
        <form onSubmit={onResend} noValidate className="space-y-4">
          <FormError error={resend.error} title="Could not send the message" />
          <TextField
            label="Email address"
            type="email"
            autoComplete="username"
            autoFocus
            placeholder="person@example.com"
            error={errors.email}
            {...register('email')}
          />
          <SubmitButton pending={isSubmitting || resend.isPending} pendingLabel="Sending…">
            Send a new link
          </SubmitButton>
        </form>
      )}
    </AuthCard>
  )
}
