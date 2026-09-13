import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'

/**
 * Where a failed permission check lands.
 *
 * It says the access was refused and nothing about what exists behind it,
 * which is the same rule the backend follows.
 */
export function ForbiddenPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="space-y-1">
        <p className="text-muted-foreground text-sm font-medium">403</p>
        <h1 className="text-lg font-semibold">You do not have access to this page</h1>
        <p className="text-muted-foreground max-w-sm text-sm">
          If you think you should, ask an administrator for the permission it needs.
        </p>
      </div>
      <Button asChild>
        <Link to={paths.root}>Go back</Link>
      </Button>
    </div>
  )
}
