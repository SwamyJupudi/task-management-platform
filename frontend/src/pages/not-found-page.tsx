import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="space-y-1">
        <p className="text-muted-foreground text-sm font-medium">404</p>
        <h1 className="text-lg font-semibold">We could not find that page</h1>
        <p className="text-muted-foreground max-w-sm text-sm">
          The link may be out of date, or the page may have been moved.
        </p>
      </div>
      <Button asChild>
        <Link to={paths.root}>Go back</Link>
      </Button>
    </div>
  )
}
