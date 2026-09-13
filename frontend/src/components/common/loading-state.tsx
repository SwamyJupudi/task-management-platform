import { Loader2Icon } from 'lucide-react'

import { cn } from '@/lib/utils'

/**
 * The inline wait, for a panel or a page body that is still loading.
 *
 * `FullPageSpinner` is the whole-window equivalent and is what a route guard
 * renders while it decides. This one sits inside the shell, so it must not
 * claim the height of the viewport.
 */
export function LoadingState({
  label = 'Loading',
  className,
}: {
  label?: string
  className?: string
}) {
  return (
    <div
      className={cn('flex flex-col items-center justify-center gap-3 py-12', className)}
      role="status"
      aria-live="polite"
    >
      <Loader2Icon className="size-5 animate-spin text-muted-foreground" aria-hidden="true" />
      <p className="text-sm text-muted-foreground">{label}</p>
    </div>
  )
}
