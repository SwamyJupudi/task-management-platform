import { TriangleAlertIcon } from 'lucide-react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { ApiError, toUserMessage } from '@/lib/api'

/**
 * The banner a rejected submission puts above a form.
 *
 * The wording is always the backend's, through `toUserMessage`: it has already
 * decided what is safe to show, and its messages are the ones the requirements
 * expect a user to read — "The email address or password is incorrect", "Please
 * verify your email address before signing in". Anything that is not a
 * deliberate `ApiError` collapses to one generic line, so no internal detail is
 * ever rendered.
 *
 * The request id is shown whenever the response carried one. It is the only
 * handle support has for finding the matching server log, and it is useless to
 * the user unless they can see it.
 */
export function FormError({
  error,
  title = 'That did not work',
}: {
  error: unknown
  title?: string
}) {
  if (!error) return null

  const requestId = error instanceof ApiError ? error.requestId : undefined

  return (
    <Alert variant="destructive">
      <TriangleAlertIcon aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p className="text-foreground/80">{toUserMessage(error)}</p>
        {requestId ? (
          <p className="font-mono text-xs break-all text-muted-foreground/80">
            Reference: {requestId}
          </p>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}
