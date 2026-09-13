import { Fragment, useMemo, useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { cn } from '@/lib/utils'

import {
  BreadcrumbTitleContext,
  pathTitles,
  segmentTitles,
  useBreadcrumbTitle,
  type BreadcrumbTitle,
} from './breadcrumb-title'

/**
 * Holds the label a screen chooses for its own crumb.
 *
 * Mounted once, in the shell, so that the trail in the header and the screen
 * in the outlet share one value without either importing the other.
 */
export function BreadcrumbTitleProvider({ children }: { children: ReactNode }) {
  const [label, setLabel] = useState<string | null>(null)
  const value = useMemo<BreadcrumbTitle>(() => ({ label, setLabel }), [label])

  return <BreadcrumbTitleContext.Provider value={value}>{children}</BreadcrumbTitleContext.Provider>
}

interface Crumb {
  label: string
  /** Absent on the final crumb, which is the current page. */
  to?: string
}

/** `roles-and-permissions` reads better than it does raw, and a UUID is left alone. */
function humanise(segment: string): string {
  const spaced = decodeURIComponent(segment).replace(/[-_]/g, ' ')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

/**
 * Derives the trail from the URL.
 *
 * From the path rather than from route metadata, because the router is the
 * component form (`<Routes>`) rather than a data router, so `useMatches` and
 * route handles are not available. The path carries the same information: the
 * workspace segment names the workspace, and every segment after it is either
 * a known word or a record the screen names for itself.
 */
function useCrumbs(): Crumb[] {
  const { pathname } = useLocation()
  const workspace = useActiveWorkspace()
  const override = useBreadcrumbTitle()

  return useMemo(() => {
    const parts = pathname.split('/').filter(Boolean)
    const crumbs: Crumb[] = []

    let base = ''
    let rest = parts

    if (parts[0] === 'w' && parts[1]) {
      base = `/w/${parts[1]}`
      crumbs.push({
        label: workspace?.workspaceName ?? humanise(parts[1]),
        to: `${base}/dashboard`,
      })
      rest = parts.slice(2)
    }

    let accumulated = base
    rest.forEach((segment, index) => {
      accumulated += `/${segment}`
      const last = index === rest.length - 1
      // The whole path first, then the segment: `users` is "People" in a
      // workspace and "User management" under `/admin`.
      const known = pathTitles[accumulated] ?? segmentTitles[segment]
      const label = known ?? (last && override ? override : humanise(segment))
      crumbs.push(last ? { label } : { label, to: accumulated })
    })

    return crumbs
  }, [pathname, workspace, override])
}

/**
 * Where the user is, and the way back up.
 *
 * Collapsed to the current page below `md`: a header fourteen pixels tall has
 * no room for a trail on a phone, and the back gesture already covers the way
 * out.
 */
export function Breadcrumbs({ className }: { className?: string }) {
  const crumbs = useCrumbs()
  if (crumbs.length === 0) return null

  const current = crumbs[crumbs.length - 1]

  return (
    <>
      <span className="truncate text-sm font-medium md:hidden">{current?.label}</span>

      <Breadcrumb className={cn('hidden min-w-0 md:block', className)}>
        <BreadcrumbList className="flex-nowrap">
          {crumbs.map((crumb, index) => (
            // The separator is a sibling list item rather than a child of the
            // crumb: nesting one <li> inside another is invalid markup, and a
            // screen reader announces the count wrongly when it happens.
            <Fragment key={`${crumb.label}-${index}`}>
              {index > 0 ? <BreadcrumbSeparator /> : null}
              <BreadcrumbItem className="min-w-0">
                {crumb.to ? (
                  <BreadcrumbLink asChild className="truncate">
                    <Link to={crumb.to}>{crumb.label}</Link>
                  </BreadcrumbLink>
                ) : (
                  <BreadcrumbPage className="truncate">{crumb.label}</BreadcrumbPage>
                )}
              </BreadcrumbItem>
            </Fragment>
          ))}
        </BreadcrumbList>
      </Breadcrumb>
    </>
  )
}
