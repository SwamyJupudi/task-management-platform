import { env } from '@/config/env'
import { cn } from '@/lib/utils'
import { useUiStore } from '@/stores/ui-store'

import { SidebarNav } from './sidebar-nav'
import { WorkspaceSwitcher } from './workspace-switcher'

/**
 * The navigation rail, on a viewport wide enough to keep one permanently.
 *
 * Hidden below `md`, where the same navigation is reached through the drawer
 * in `MobileNav` instead. Sticky and the full height of the viewport, so the
 * rail stays put while a long list scrolls beside it.
 *
 * Collapsed width is a preference, persisted in the interface store, because
 * it belongs to the browser rather than to the account.
 */
export function AppSidebar() {
  const collapsed = useUiStore((state) => state.sidebarCollapsed)

  return (
    <aside
      data-collapsed={collapsed}
      className={cn(
        'sticky top-0 hidden h-svh shrink-0 flex-col border-r border-sidebar-border bg-sidebar transition-[width] duration-200 md:flex',
        collapsed ? 'w-16' : 'w-64',
      )}
    >
      <div
        className={cn('flex h-14 shrink-0 items-center px-3', collapsed && 'justify-center px-0')}
      >
        <span className={cn('truncate text-sm font-semibold', collapsed && 'sr-only')}>
          {env.appName}
        </span>
      </div>

      <div className={cn('px-2 pb-2', collapsed && 'px-1')}>
        <WorkspaceSwitcher collapsed={collapsed} />
      </div>

      <SidebarNav collapsed={collapsed} />
    </aside>
  )
}
