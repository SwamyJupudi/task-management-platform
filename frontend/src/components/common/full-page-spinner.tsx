import { Loader2Icon } from 'lucide-react'

/** The whole-screen wait, used while a route decides whether it may render. */
export function FullPageSpinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div
      className="flex min-h-svh flex-col items-center justify-center gap-3"
      role="status"
      aria-live="polite"
    >
      <Loader2Icon className="text-muted-foreground size-6 animate-spin" aria-hidden="true" />
      <p className="text-muted-foreground text-sm">{label}</p>
    </div>
  )
}
