import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * The title block every screen inside the shell opens with.
 *
 * One component so that headings, spacing and the position of a screen's
 * primary action are the same everywhere, rather than re-decided per page.
 */
export function PageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  /** The screen's primary controls, right-aligned on a wide viewport. */
  actions?: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn('flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between', className)}
    >
      <div className="min-w-0 space-y-1">
        <h1 className="truncate text-xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </div>
  )
}
