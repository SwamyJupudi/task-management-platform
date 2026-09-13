import { TriangleAlertIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { ApiError, toUserMessage } from '@/lib/api'
import { cn } from '@/lib/utils'

/**
 * The inline "this panel failed" state.
 *
 * The message always comes from `toUserMessage`, which returns the
 * backend's own user-facing wording for a deliberate error and one generic
 * line for anything else. Internal detail is never rendered, as section 20
 * of the requirements demands.
 *
 * The request id is shown when there is one, because it is the key support
 * needs to find the matching server log.
 */
export function ErrorState({
  error,
  onRetry,
  className,
}: {
  error: unknown
  onRetry?: () => void
  className?: string
}) {
  const requestId = error instanceof ApiError ? error.requestId : undefined

  return (
    <div
      className={cn(
        'border-destructive/30 bg-destructive/5 flex flex-col items-center justify-center gap-3 rounded-lg border px-6 py-10 text-center',
        className,
      )}
      role="alert"
    >
      <TriangleAlertIcon className="text-destructive size-6" aria-hidden="true" />
      <div className="space-y-1">
        <h3 className="text-sm font-medium">Something went wrong</h3>
        <p className="text-muted-foreground mx-auto max-w-sm text-sm">{toUserMessage(error)}</p>
        {requestId ? (
          <p className="text-muted-foreground/70 font-mono text-xs">Reference: {requestId}</p>
        ) : null}
      </div>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      ) : null}
    </div>
  )
}
