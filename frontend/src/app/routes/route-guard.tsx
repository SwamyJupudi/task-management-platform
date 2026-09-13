import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { FullPageSpinner } from '@/components/common/full-page-spinner'
import { usePermissions } from '@/hooks/use-permissions'
import { useSessionStore } from '@/stores/session-store'

import { paths } from './paths'

/**
 * The authentication and permission gate every private route sits behind.
 *
 * It reads the session store and decides; it never calls the API. Populating
 * the store is the authentication feature's job, and until that feature
 * exists the status stays `unknown` and these guards hold at the spinner.
 * That is the intended scaffold behaviour, not a bug.
 *
 * A guard hides a screen. It does not protect the data behind it: every
 * route here maps to endpoints that repeat the same check server-side.
 */

interface RequireAuthProps {
  children?: ReactNode
}

export function RequireAuth({ children }: RequireAuthProps) {
  const status = useSessionStore((state) => state.status)
  const location = useLocation()

  // The first `/auth/me` has not answered yet. Redirecting now would bounce a
  // signed-in user to the login screen on every reload.
  if (status === 'unknown') return <FullPageSpinner label="Checking your session" />

  if (status === 'anonymous') {
    // `from` lets sign-in return the user to the page they actually wanted.
    return <Navigate to={paths.auth.login} state={{ from: location }} replace />
  }

  return children ? <>{children}</> : <Outlet />
}

interface RequirePermissionProps {
  /** Permission codes, e.g. `project:read`. */
  codes: readonly string[]
  /** When true the user needs every code rather than any one of them. */
  requireAll?: boolean
  children?: ReactNode
}

export function RequirePermission({ codes, requireAll = false, children }: RequirePermissionProps) {
  const { hasAll, hasAny, ready } = usePermissions()

  if (!ready) return <FullPageSpinner label="Checking your access" />

  const permitted = requireAll ? hasAll(codes) : hasAny(codes)
  if (!permitted) return <Navigate to={paths.forbidden} replace />

  return children ? <>{children}</> : <Outlet />
}

/** Keeps a signed-in user off the sign-in and registration screens. */
export function RequireAnonymous({ children }: RequireAuthProps) {
  const status = useSessionStore((state) => state.status)

  if (status === 'unknown') return <FullPageSpinner label="Checking your session" />
  if (status === 'authenticated') return <Navigate to={paths.app.dashboard} replace />

  return children ? <>{children}</> : <Outlet />
}
