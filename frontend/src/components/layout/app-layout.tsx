import { Suspense, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

import { ErrorBoundary } from '@/components/common/error-boundary'
import { LoadingState } from '@/components/common/loading-state'
import { TooltipProvider } from '@/components/ui/tooltip'
import { useMediaQuery } from '@/hooks/use-media-query'

import { AppHeader } from './app-header'
import { AppSidebar } from './app-sidebar'
import { BreadcrumbTitleProvider } from './breadcrumbs'
import { MobileNav } from './mobile-nav'

/**
 * The shell every signed-in screen renders inside.
 *
 * Two navigations for two viewports: a rail that is always there from `md`
 * upwards, and a drawer below it. Both render the same `SidebarNav`, so an
 * entry cannot exist on one and not the other.
 *
 * The boundary and the Suspense fallback sit inside the shell rather than
 * around it, so a failed or still-loading screen keeps its navigation instead
 * of blanking the window — a user who lands on a broken page can still leave
 * it.
 *
 * The drawer's open state is local rather than in the interface store. It is
 * not a preference, it means nothing after a reload, and the only two things
 * that touch it are both in this subtree.
 */
export function AppLayout() {
  const { pathname } = useLocation()

  // The path the drawer was opened on, rather than a boolean. A drawer left
  // open over the screen it navigated to would hide it, and deriving "open"
  // from the path closes it on every way out rather than only on a click:
  // the back button and a redirect from a guard are covered too, without an
  // effect that would set state during a render it just caused.
  const [openedAt, setOpenedAt] = useState<string | null>(null)

  // And closed outright once the rail is on screen. The drawer is a modal
  // dialog, so leaving it open behind a viewport that has grown past `md`
  // would leave its overlay covering a page whose navigation is now visible
  // anyway. Tailwind's `md` is 48rem.
  const railVisible = useMediaQuery('(min-width: 48rem)')
  const mobileNavOpen = openedAt === pathname && !railVisible

  return (
    <TooltipProvider>
      <BreadcrumbTitleProvider>
        <div className="flex min-h-svh bg-background">
          <a
            href="#main"
            className="sr-only bg-background focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:px-3 focus:py-2 focus:ring-2 focus:ring-ring"
          >
            Skip to content
          </a>

          <AppSidebar />
          <MobileNav
            open={mobileNavOpen}
            onOpenChange={(open) => setOpenedAt(open ? pathname : null)}
          />

          <div className="flex min-w-0 flex-1 flex-col">
            <AppHeader onOpenMobileNav={() => setOpenedAt(pathname)} />

            <main id="main" className="flex-1 p-4 md:p-6">
              <ErrorBoundary>
                <Suspense fallback={<LoadingState />}>
                  <Outlet />
                </Suspense>
              </ErrorBoundary>
            </main>
          </div>
        </div>
      </BreadcrumbTitleProvider>
    </TooltipProvider>
  )
}
