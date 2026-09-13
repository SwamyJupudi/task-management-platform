import { NavLink } from 'react-router-dom'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useActiveWorkspaceSlug } from '@/hooks/use-active-workspace'
import { useIsPlatformAdmin, usePermissions } from '@/hooks/use-permissions'
import { cn } from '@/lib/utils'

import { platformNav, workspaceNav, type NavItem, type NavSection } from './nav-items'

/**
 * The navigation itself, shared by the desktop rail and the mobile sheet.
 *
 * One component so the two cannot drift: an entry added to `nav-items.ts`
 * appears in both, filtered by the same permission check, with the same active
 * styling. The rail passes `collapsed`; the sheet never does, because a drawer
 * that has just been opened has no reason to hide its labels.
 *
 * Entries are filtered by permission, which is what the requirements call
 * permission-based UI: a user is never shown a destination the API would
 * refuse them. Hiding is a courtesy, not a control — every one of these
 * screens repeats the check server-side.
 */

interface NavProps {
  collapsed?: boolean
  /** Closes the mobile drawer once a destination has been chosen. */
  onNavigate?: () => void
}

function NavEntry({ item, collapsed, onNavigate }: { item: NavItem } & NavProps) {
  const slug = useActiveWorkspaceSlug()

  const link = (
    <NavLink
      to={item.to(slug ?? '')}
      end={item.end}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
          'focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
          isActive
            ? 'bg-sidebar-accent text-sidebar-accent-foreground'
            : 'text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground',
          collapsed && 'justify-center px-2',
        )
      }
    >
      <item.icon className="size-4 shrink-0" aria-hidden="true" />
      <span className={cn('truncate', collapsed && 'sr-only')}>{item.label}</span>
    </NavLink>
  )

  // A collapsed rail is icons only, so the label has to be reachable some other
  // way. The tooltip is that way for a pointer; the `sr-only` span above is it
  // for a screen reader.
  if (!collapsed) return link

  return (
    <Tooltip>
      <TooltipTrigger asChild>{link}</TooltipTrigger>
      <TooltipContent side="right">{item.label}</TooltipContent>
    </Tooltip>
  )
}

function Section({ section, collapsed, onNavigate }: { section: NavSection } & NavProps) {
  const { hasAny } = usePermissions()
  const isPlatformAdmin = useIsPlatformAdmin()

  if (section.platformOnly && !isPlatformAdmin) return null

  const visible = section.items.filter(
    (item) => item.permissions.length === 0 || hasAny(item.permissions),
  )
  if (visible.length === 0) return null

  return (
    <div className="space-y-1">
      {collapsed ? (
        <span className="sr-only">{section.label}</span>
      ) : (
        <p className="px-3 text-xs font-medium tracking-wide text-muted-foreground/70 uppercase">
          {section.label}
        </p>
      )}
      <ul className="space-y-1">
        {visible.map((item) => (
          <li key={`${section.id}-${item.label}`}>
            <NavEntry item={item} collapsed={collapsed} onNavigate={onNavigate} />
          </li>
        ))}
      </ul>
    </div>
  )
}

export function SidebarNav({ collapsed = false, onNavigate }: NavProps) {
  const slug = useActiveWorkspaceSlug()

  // Every workspace destination needs a slug to point at. An account with a
  // platform role but no membership still has the admin panel, so the platform
  // sections are rendered either way.
  const sections = slug ? [...workspaceNav, ...platformNav] : platformNav

  return (
    <nav aria-label="Main" className="flex flex-1 flex-col gap-5 overflow-y-auto px-3 py-4">
      {sections.map((section) => (
        <Section key={section.id} section={section} collapsed={collapsed} onNavigate={onNavigate} />
      ))}
    </nav>
  )
}
