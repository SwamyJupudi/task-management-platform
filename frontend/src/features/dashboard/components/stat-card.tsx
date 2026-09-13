import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/**
 * One headline figure.
 *
 * `tone` marks a number that is bad news rather than merely large. Overdue work
 * reads in the destructive colour; nothing else does, so the colour keeps its
 * meaning instead of becoming decoration.
 *
 * The figure is `tabular-nums` so a row of cards does not shift as the numbers
 * refresh, and the loading state is a skeleton the size of the value rather
 * than a spinner, so the layout is the same before and after it arrives.
 */
export function StatCard({
  label,
  value,
  icon: Icon,
  hint,
  tone = 'default',
  loading = false,
  className,
}: {
  label: string
  value: number | string
  icon?: LucideIcon
  /** A second line under the figure: what it is of, or how it was measured. */
  hint?: ReactNode
  tone?: 'default' | 'destructive'
  loading?: boolean
  className?: string
}) {
  return (
    <Card className={className}>
      <CardContent className="flex items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <p className="truncate text-sm font-medium text-muted-foreground">{label}</p>
          {loading ? (
            <Skeleton className="h-8 w-16" />
          ) : (
            <p
              className={cn(
                'text-2xl font-semibold tabular-nums',
                tone === 'destructive' && 'text-destructive',
              )}
            >
              {typeof value === 'number' ? value.toLocaleString() : value}
            </p>
          )}
          {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
        </div>
        {Icon ? (
          <Icon
            className={cn(
              'size-4 shrink-0',
              tone === 'destructive' ? 'text-destructive' : 'text-muted-foreground',
            )}
            aria-hidden="true"
          />
        ) : null}
      </CardContent>
    </Card>
  )
}
