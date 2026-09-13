import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * The "there is nothing here yet" panel.
 *
 * One component so that an empty list reads the same everywhere, which is
 * what the requirements mean by empty states and reusable components.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: {
  icon?: LucideIcon
  title: string
  description?: string
  /** Usually the button that creates the first record. */
  action?: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'border-border flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center',
        className,
      )}
    >
      {Icon ? <Icon className="text-muted-foreground size-8" aria-hidden="true" /> : null}
      <div className="space-y-1">
        <h3 className="text-sm font-medium">{title}</h3>
        {description ? (
          <p className="text-muted-foreground mx-auto max-w-sm text-sm">{description}</p>
        ) : null}
      </div>
      {action}
    </div>
  )
}
