import { zodResolver } from '@hookform/resolvers/zod'
import { CircleCheckIcon, Loader2Icon, MailIcon } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { useSessionStore } from '@/stores/session-store'

import { AuthCard, SubmitButton } from '../components/auth-card'
import { FormError } from '../components/form-error'
import { TextField } from '../components/text-field'
import { applyFieldErrors, useAcceptInvitation, useInvitationPreview, useLogout } from '../hooks'
import { acceptInvitationSchema, type AcceptInvitationValues } from '../schemas'

/**
 * Join a workspace from an invitation link.
 *
 * Reached from the message the backend sends, which points at
 * `MAIL_LINK_BASE_URL + /invitations/accept?token=…`. The token is read from
 * the query string, shown to nobody, and posted in a body when it is spent.
 *
 * Reachable with or without a session, because an invitation reaches two kinds
 * of person and the backend treats them differently:
 *
 *  - **No account yet.** The form below creates one and joins in a single
 *    request. The address is the invitation's and is not editable. The account
 *    arrives already verified — the token went to that address and nowhere
 *    else, so redeeming it proves what a verification message would.
 *  - **An account already exists.** The backend insists the caller *is* that
 *    account, reading the bearer token to decide, because otherwise holding the
 *    link would be enough to add somebody else's account to a workspace. So
 *    this screen asks them to sign in as the invited address first, and says
 *    plainly when the session it finds belongs to somebody else.
 *
 * Every refusal comes from the server and is rendered as the server worded it.
 * The one thing this screen adds is the `TOKEN_EXPIRED` / `TOKEN_INVALID`
 * split, because the two have different remedies: an expired invitation can be
 * sent again, while an unusable one may have been withdrawn, already accepted,
 * or never real. The backend collapses those three into one code on purpose —
 * telling a stranger which would confirm that a particular invitation existed —
 * so the wording here covers all three rather than guessing between them.
 */
export function AcceptInvitationPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const token = searchParams.get('token')
  const preview = useInvitationPreview(token)
  const accept = useAcceptInvitation()
  const logout = useLogout()

  const status = useSessionStore((state) => state.status)
  const currentEmail = useSessionStore((state) => state.user?.email ?? null)
  const memberships = useSessionStore((state) => state.memberships)

  const signedIn = status === 'authenticated'
  const invitation = preview.data

  // Case-insensitively, because an address is not case sensitive in practice
  // and the backend compares them folded.
  const sameAccount =
    signedIn &&
    currentEmail !== null &&
    invitation !== undefined &&
    currentEmail.toLowerCase() === invitation.email.toLowerCase()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AcceptInvitationValues>({
    resolver: zodResolver(acceptInvitationSchema),
    defaultValues: { firstName: '', lastName: '', password: '', confirmPassword: '' },
  })

  /**
   * Sends the reader where they now belong.
   *
   * The slug comes from the membership list the acceptance refreshed, matched
   * on the workspace the server named. If it is somehow not there, the root
   * redirect picks a workspace rather than this screen guessing a slug that
   * might 404.
   */
  const goToWorkspace = (workspaceId: string) => {
    const joined = useSessionStore
      .getState()
      .memberships.find((membership) => membership.workspaceId === workspaceId)

    void navigate(joined ? paths.workspace.dashboard(joined.workspaceSlug) : paths.root, {
      replace: true,
    })
  }

  const joinWithExistingAccount = async () => {
    if (!token) return
    const result = await accept.mutateAsync({ body: { token }, authenticated: true })
    goToWorkspace(result.workspaceId)
  }

  const joinWithNewAccount = handleSubmit(async (values) => {
    if (!token || !invitation) return
    try {
      const result = await accept.mutateAsync({
        body: {
          token,
          password: values.password,
          firstName: values.firstName,
          lastName: values.lastName,
        },
        authenticated: false,
        signInWith: invitation.email,
      })

      if (result.signedIn) {
        goToWorkspace(result.workspaceId)
        return
      }
      // Joined, but the courtesy sign-in did not take. Sending them to the
      // sign-in screen is honest; pretending it failed would not be.
      void navigate(paths.auth.login, { replace: true })
    } catch (error) {
      applyFieldErrors(error, setError, ['firstName', 'lastName', 'password'])
    }
  })

  // --- the link itself ------------------------------------------------------

  if (!token) {
    return (
      <AuthCard
        title="This page needs an invitation link"
        description="Open the link from your invitation email. It carries the token that identifies the invitation."
        footer={
          <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
            Back to sign in
          </Link>
        }
      >
        <Alert>
          <MailIcon aria-hidden="true" />
          <AlertTitle>Nothing to accept</AlertTitle>
          <AlertDescription>
            <p>
              If the link will not open, ask whoever invited you to send another one — inviting the
              same address again replaces the old invitation.
            </p>
          </AlertDescription>
        </Alert>
      </AuthCard>
    )
  }

  if (preview.isPending) {
    return (
      <AuthCard title="Checking your invitation">
        <div className="flex items-center gap-3 py-2" role="status" aria-live="polite">
          <Loader2Icon className="size-4 animate-spin text-muted-foreground" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">This will only take a moment.</p>
        </div>
      </AuthCard>
    )
  }

  if (preview.isError) {
    const expired = preview.error instanceof ApiError && preview.error.code === 'TOKEN_EXPIRED'

    return (
      <AuthCard
        title={expired ? 'That invitation has expired' : 'That invitation cannot be used'}
        description={
          expired
            ? 'Invitations are valid for a limited time. Ask whoever invited you to send another one.'
            : 'It may have been withdrawn, already accepted, or the link may be incomplete. Ask whoever invited you to send another one.'
        }
        footer={
          <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
            Back to sign in
          </Link>
        }
      >
        <FormError error={preview.error} title="Could not open this invitation" />
        <Button variant="outline" className="w-full" onClick={() => void preview.refetch()}>
          Try again
        </Button>
      </AuthCard>
    )
  }

  if (!invitation) return null

  // --- the invitation, and the two ways to take it up -----------------------

  const details = (
    <div className="space-y-2 rounded-lg border border-border bg-muted/30 p-3 text-sm">
      <p className="font-medium">{invitation.workspaceName}</p>
      <p className="text-muted-foreground">
        Invited as <span className="text-foreground">{invitation.email}</span>
      </p>
      <p className="flex items-center gap-2 text-muted-foreground">
        Role
        <Badge variant="outline">{invitation.roleSlug}</Badge>
      </p>
    </div>
  )

  // An account exists for the invited address, and this browser is signed in as
  // somebody else. The backend would refuse; saying so here is kinder than
  // letting them press a button that cannot work.
  if (invitation.accountExists && signedIn && !sameAccount) {
    const alreadyJoined = memberships.some(
      (membership) => membership.workspaceName === invitation.workspaceName,
    )

    return (
      <AuthCard
        title="Signed in as somebody else"
        description={`This invitation was sent to ${invitation.email}, but you are signed in as ${currentEmail}.`}
      >
        {details}
        <Alert>
          <AlertTitle>Sign in as the invited account</AlertTitle>
          <AlertDescription>
            <p>
              An invitation can only be accepted by the account it was sent to. Sign out, sign back
              in as {invitation.email}, and open this link again.
              {alreadyJoined
                ? ' You already belong to a workspace with this name, which may be the same one.'
                : ''}
            </p>
          </AlertDescription>
        </Alert>
        <Button
          variant="outline"
          className="w-full"
          disabled={logout.isPending}
          onClick={() => logout.mutate()}
        >
          {logout.isPending ? 'Signing out…' : 'Sign out'}
        </Button>
      </AuthCard>
    )
  }

  // An account exists and nobody is signed in. The token is safe in the URL
  // for the round trip to the sign-in screen and back, which is what `from`
  // arranges: `RequireAnonymous` returns the user to exactly this link.
  if (invitation.accountExists && !signedIn) {
    return (
      <AuthCard
        title={`Join ${invitation.workspaceName}`}
        description="You already have an account for this address. Sign in and open this link again to join."
      >
        {details}
        <Button asChild className="w-full">
          <Link
            to={paths.auth.login}
            // `RequireAnonymous` rebuilds the destination as
            // pathname + search + hash, so every part has to be present; an
            // absent hash would be concatenated as the string "undefined".
            state={{
              from: {
                pathname: paths.auth.acceptInvitation,
                search: `?token=${encodeURIComponent(token)}`,
                hash: '',
              },
            }}
          >
            Sign in as {invitation.email}
          </Link>
        </Button>
      </AuthCard>
    )
  }

  // An account exists and it is this one. Nothing to fill in.
  if (invitation.accountExists && sameAccount) {
    return (
      <AuthCard
        title={`Join ${invitation.workspaceName}`}
        description="Accepting adds this workspace to your account."
      >
        {details}
        <FormError error={accept.error} title="Could not accept this invitation" />
        <Button
          className="w-full"
          size="lg"
          disabled={accept.isPending}
          aria-busy={accept.isPending}
          onClick={() => void joinWithExistingAccount()}
        >
          {accept.isPending ? (
            <>
              <Loader2Icon className="size-4 animate-spin" aria-hidden="true" />
              Joining…
            </>
          ) : (
            `Join ${invitation.workspaceName}`
          )}
        </Button>
      </AuthCard>
    )
  }

  // No account for the invited address: create one and join in a single step.
  return (
    <AuthCard
      title={`Join ${invitation.workspaceName}`}
      description="Choose a password to finish setting up your account."
      footer={
        <Link to={paths.auth.login} className="font-medium text-foreground hover:underline">
          Already have an account? Sign in
        </Link>
      }
    >
      {details}

      <Alert variant="success">
        <CircleCheckIcon aria-hidden="true" />
        <AlertTitle>No separate verification needed</AlertTitle>
        <AlertDescription>
          <p>This link was sent to {invitation.email}, so that address is confirmed by using it.</p>
        </AlertDescription>
      </Alert>

      <form onSubmit={joinWithNewAccount} noValidate className="space-y-4">
        <FormError error={accept.error} title="Could not accept this invitation" />

        <div className="grid gap-4 sm:grid-cols-2">
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
          label="Password"
          type="password"
          autoComplete="new-password"
          error={errors.password}
          {...register('password')}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword}
          {...register('confirmPassword')}
        />

        <SubmitButton
          pending={isSubmitting || accept.isPending}
          pendingLabel="Creating your account…"
        >
          Create account and join
        </SubmitButton>
      </form>
    </AuthCard>
  )
}
