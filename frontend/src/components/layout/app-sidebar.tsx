import { NavLink } from 'react-router-dom'

import { usePermissions, useIsPlatformAdmin } from '@/hooks/use-permissions'
import { cn } from '@/lib/utils'
import { useUiStore } from '@/stores/ui-store'

import { adminNav, primaryNav, type NavItem } from './nav-items'

/**
 * The primary navigation rail.
 *
 * Entries are filtered by permission, which is what the requirements call
 * permission-based UI: a user is never shown a destination the API would
 * refuse them. Hiding is a courtesy, not a control.
 */
function NavSection({ items, collapsed }: { items: readonly NavItem[]; collapsed: boolean }) {
  const { hasAny } = usePermissions()
  const isPlatformAdmin = useIsPlatformAdmin()

  const visible = items.filter((item) => {
    if (item.platformOnly && !isPlatformAdmin) return false
    return item.permissions.length === 0 || hasAny(item.permissions)
  })

  if (visible.length === 0) return null

  return (
    <ul className="space-y-1">
      {visible.map((item) => (
        <li key={item.to}>
          <NavLink
            to={item.to}
            title={collapsed ? item.label : undefined}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                'focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none',
                isActive
                  ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                  : 'text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground',
                collapsed && 'justify-center px-2',
              )
            }
          >
            <item.icon className="size-4 shrink-0" aria-hidden="true" />
            <span className={cn(collapsed && 'sr-only')}>{item.label}</span>
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

export function AppSidebar() {
  const collapsed = useUiStore((state) => state.sidebarCollapsed)

  return (
    <aside
      className={cn(
        'bg-sidebar border-sidebar-border hidden shrink-0 border-r md:flex md:flex-col',
        collapsed ? 'w-16' : 'w-60',
      )}
    >
      <nav aria-label="Main" className="flex flex-1 flex-col gap-6 overflow-y-auto p-3">
        <NavSection items={primaryNav} collapsed={collapsed} />
        <NavSection items={adminNav} collapsed={collapsed} />
      </nav>
    </aside>
  )
}
