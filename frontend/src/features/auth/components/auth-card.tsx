import type { ReactNode } from 'react'
import { Loader2Icon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * The frame every authentication screen sits in.
 *
 * `AuthLayout` already centres a narrow column; this supplies the heading, the
 * explanatory line and the footer link, so the five screens differ only in
 * their fields rather than in their chrome.
 */
export function AuthCard({
  title,
  description,
  children,
  footer,
}: {
  title: string
  description?: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="space-y-4">{children}</CardContent>
      {footer ? (
        <CardContent className="text-center text-sm text-muted-foreground">{footer}</CardContent>
      ) : null}
    </Card>
  )
}

/**
 * The submit button, with the pending state the requirements ask for.
 *
 * Disabled while the request is in flight, which is what stops a second
 * sign-in attempt counting against the lockout threshold, and a second
 * registration creating nothing but a duplicate-address error. `aria-busy`
 * announces the wait; the label changes so it is not only a spinner.
 */
export function SubmitButton({
  pending,
  pendingLabel,
  children,
  disabled,
}: {
  pending: boolean
  pendingLabel: string
  children: ReactNode
  disabled?: boolean
}) {
  return (
    <Button
      type="submit"
      size="lg"
      className="w-full"
      disabled={pending || disabled}
      aria-busy={pending}
    >
      {pending ? (
        <>
          <Loader2Icon className="size-4 animate-spin" aria-hidden="true" />
          {pendingLabel}
        </>
      ) : (
        children
      )}
    </Button>
  )
}
