import { NavLink } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { useActiveWorkspaceSlug } from '@/hooks/use-active-workspace'
import { cn } from '@/lib/utils'

import { useReportPermissions } from '../hooks'

/**
 * Moving between the reports.
 *
 * Links rather than tabs, because each report is its own route: a filtered
 * overdue report is a URL somebody can send, the back button steps between
 * reports the way it should, and the breadcrumb trail names where you are.
 * A tab strip holding six panels in one route would give up all three.
 *
 * The team report is hidden from anybody without the three codes its endpoint
 * insists on, so the strip does not offer a screen that would refuse them. The
 * project report is shown regardless: its own screen explains the refusal,
 * because losing `project:read` while keeping `task:read` is an unusual enough
 * combination that a silently missing link would be more confusing than a
 * sentence saying why.
 *
 * It scrolls sideways below `sm` rather than wrapping into two rows, which
 * keeps the current report in the same place at every width.
 */

interface ReportLink {
  to: (slug: string) => string
  label: string
  end?: boolean
}

const LINKS: readonly ReportLink[] = [
  { to: paths.workspace.reports, label: 'Overview', end: true },
  { to: paths.workspace.reportProjects, label: 'Projects' },
  { to: paths.workspace.reportTasks, label: 'Tasks' },
  { to: paths.workspace.reportOverdue, label: 'Overdue' },
  { to: paths.workspace.reportWorkload, label: 'Workload' },
]

export function ReportNav() {
  const slug = useActiveWorkspaceSlug()
  const { canReadTeamPerformance } = useReportPermissions()

  if (slug === null) return null

  const links = canReadTeamPerformance
    ? [...LINKS, { to: paths.workspace.reportTeams, label: 'Teams' }]
    : LINKS

  return (
    <nav aria-label="Reports" className="-mx-1 overflow-x-auto">
      <ul className="flex w-max min-w-full items-center gap-1 px-1">
        {links.map((link) => (
          <li key={link.label}>
            <NavLink
              to={link.to(slug)}
              end={link.end ?? false}
              className={({ isActive }) =>
                cn(
                  'inline-flex h-8 items-center rounded-md px-3 text-sm whitespace-nowrap transition-colors',
                  'focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
                  isActive
                    ? 'bg-secondary font-medium text-secondary-foreground'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                )
              }
            >
              {link.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
