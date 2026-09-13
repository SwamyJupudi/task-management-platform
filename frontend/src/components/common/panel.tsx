import type { ReactNode } from 'react'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/**
 * A dashboard card, and the four states its contents can be in.
 *
 * One component because a dashboard is mostly a grid of panels that each load
 * separately, and without this every one of them would re-decide what loading
 * looks like, what an empty result says and whether a failure takes the page
 * down with it. Here it is decided once: a panel that fails shows its own error
 * and leaves the rest of the screen alone, which is the whole reason the panels
 * have separate queries.
 *
 * The order matters. Error before loading, so a refetch after a failure does
 * not flash a skeleton over a message the user is still reading; loading before
 * empty, so an unloaded panel never claims there is nothing to show.
 */
export function Panel({
  title,
  description,
  action,
  isLoading = false,
  error,
  onRetry,
  isEmpty = false,
  emptyTitle = 'Nothing to show',
  emptyDescription,
  children,
  className,
  contentClassName,
}: {
  title: ReactNode
  description?: ReactNode
  /** Usually a link to the report that holds the whole list. */
  action?: ReactNode
  isLoading?: boolean
  error?: unknown
  onRetry?: () => void
  isEmpty?: boolean
  emptyTitle?: string
  emptyDescription?: string
  children: ReactNode
  className?: string
  contentClassName?: string
}) {
  return (
    <Card className={cn('flex flex-col', className)}>
      <CardHeader>
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
        {action ? <CardAction>{action}</CardAction> : null}
      </CardHeader>

      <CardContent className={cn('flex-1', contentClassName)}>
        {error ? (
          <ErrorState error={error} onRetry={onRetry} className="border-0 bg-transparent py-6" />
        ) : isLoading ? (
          <div className="space-y-2" role="status" aria-live="polite">
            <span className="sr-only">Loading</span>
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        ) : isEmpty ? (
          <EmptyState
            title={emptyTitle}
            description={emptyDescription}
            className="border-0 px-0 py-6"
          />
        ) : (
          children
        )}
      </CardContent>
    </Card>
  )
}
