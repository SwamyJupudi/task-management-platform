import { createContext, useContext, useEffect } from 'react'

/**
 * The label a screen wants for its own breadcrumb.
 *
 * Most crumbs are named by their path segment, which is enough while every
 * segment is a fixed word. A detail route is not: `/w/acme/projects/2f9c…`
 * would read as its identifier. The screen that loads the record is the only
 * thing that knows the name, so it puts it here and the trail reads it.
 *
 * The context, not the component, so that `breadcrumbs.tsx` exports components
 * and this file exports hooks, which keeps both sides of the seam obvious.
 */

export interface BreadcrumbTitle {
  /** The label for the final crumb, or null to name it from the path. */
  label: string | null
  setLabel: (label: string | null) => void
}

export const BreadcrumbTitleContext = createContext<BreadcrumbTitle>({
  label: null,
  setLabel: () => {},
})

/** Reads the override. Used by the trail itself. */
export function useBreadcrumbTitle(): string | null {
  return useContext(BreadcrumbTitleContext).label
}

/**
 * Names the final crumb from inside a screen.
 *
 * Pass `undefined` while the record is still loading and the path segment is
 * used until it arrives. The label is cleared on unmount, so a screen can
 * never leave its name behind on the next one.
 */
export function useSetBreadcrumbTitle(label: string | null | undefined): void {
  const { setLabel } = useContext(BreadcrumbTitleContext)

  useEffect(() => {
    setLabel(label ?? null)
    return () => setLabel(null)
  }, [label, setLabel])
}

/**
 * Whole paths whose crumb is not simply its last segment.
 *
 * Checked before `segmentTitles`, because the same word means different things
 * in different places: `users` is "People" inside a workspace and "User
 * management" in the admin panel, and a trail that called both "People" would
 * be actively misleading about which one is open.
 *
 * Workspace paths are matched with the `/w/:slug` prefix already stripped.
 */
export const pathTitles: Readonly<Record<string, string>> = {
  '/admin': 'Admin',
  '/admin/users': 'User management',
  '/admin/roles': 'Roles and permissions',
  '/admin/activity': 'Activity log',
  '/admin/statistics': 'System statistics',
}

/**
 * The fixed segments, and what each is called in the trail.
 *
 * Anything absent is either a record identifier, which a screen names through
 * `useSetBreadcrumbTitle`, or a segment nobody has got to yet, which is
 * humanised rather than left blank.
 */
export const segmentTitles: Readonly<Record<string, string>> = {
  dashboard: 'Dashboard',
  projects: 'Projects',
  tasks: 'Tasks',
  mine: 'My tasks',
  teams: 'Teams',
  users: 'People',
  notifications: 'Notifications',
  reports: 'Reports',
  // The two report segments that are not already a word the trail knows.
  // `projects`, `tasks` and `teams` read correctly under Reports as they are.
  overdue: 'Overdue',
  workload: 'Workload',
  settings: 'Settings',
}
