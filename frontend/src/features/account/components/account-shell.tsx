import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { PageHeader } from '@/components/common/page-header'
import { cn } from '@/lib/utils'

/**
 * The frame the three account screens sit in.
 *
 * Outside every workspace, like the admin panel and for a related reason: an
 * account belongs to a person rather than to a tenant. Its endpoints take no
 * workspace and are gated on being signed in and nothing else, so scoping the
 * URL to one would misdescribe what these screens are.
 *
 * Links rather than tabs, because each screen is its own route: the browser's
 * back button steps between them, and "here is where you change your password"
 * is something somebody can send to a colleague.
 */

const LINKS: readonly { to: string; label: string; end?: boolean }[] = [
  { to: paths.account.root, label: 'Profile', end: true },
  { to: paths.account.password, label: 'Password' },
  { to: paths.account.sessions, label: 'Sessions' },
]

export function AccountShell({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-6">
      <PageHeader title={title} description={description} />

      <nav aria-label="Account" className="-mx-1 overflow-x-auto">
        <ul className="flex w-max min-w-full items-center gap-1 px-1">
          {LINKS.map((link) => (
            <li key={link.to}>
              <NavLink
                to={link.to}
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

      {/* A single column: these are forms and a list, and stretching them the
          width of a desktop would put the label and its field a screen apart. */}
      <div className="max-w-2xl">{children}</div>
    </div>
  )
}
