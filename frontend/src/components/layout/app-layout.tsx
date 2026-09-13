import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'

import { ErrorBoundary } from '@/components/common/error-boundary'
import { FullPageSpinner } from '@/components/common/full-page-spinner'

import { AppHeader } from './app-header'
import { AppSidebar } from './app-sidebar'

/**
 * The shell every signed-in screen renders inside.
 *
 * The boundary and the Suspense fallback sit inside the shell rather than
 * around it, so a failed or still-loading page keeps its navigation instead
 * of blanking the window.
 */
export function AppLayout() {
  return (
    <div className="flex min-h-svh flex-col">
      <a
        href="#main"
        className="bg-background focus:ring-ring sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:px-3 focus:py-2 focus:ring-2"
      >
        Skip to content
      </a>

      <AppHeader />

      <div className="flex flex-1 overflow-hidden">
        <AppSidebar />
        <main id="main" className="flex-1 overflow-y-auto p-4 md:p-6">
          <ErrorBoundary>
            <Suspense fallback={<FullPageSpinner />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  )
}
