import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'

/**
 * A `/w/:workspaceSlug` the session holds no membership for.
 *
 * It says the workspace could not be opened and nothing about whether it
 * exists, which is the same rule the backend follows: `WorkspaceAccessGuard`
 * answers 404 rather than 403 so that a stranger cannot learn a slug is real
 * by asking for it.
 */
export function WorkspaceNotFoundPage({ slug }: { slug?: string }) {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="space-y-1">
        <p className="text-sm font-medium text-muted-foreground">404</p>
        <h1 className="text-lg font-semibold">We could not open that workspace</h1>
        <p className="max-w-sm text-sm text-muted-foreground">
          {slug ? (
            <>
              Nothing you are a member of is called <span className="font-medium">{slug}</span>.
            </>
          ) : (
            'The link may be out of date, or your membership may have been removed.'
          )}
        </p>
      </div>
      <Button asChild>
        <Link to={paths.root}>Go to your workspace</Link>
      </Button>
    </div>
  )
}
