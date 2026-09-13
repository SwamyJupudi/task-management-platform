import { MenuIcon, PanelLeftIcon } from 'lucide-react'

import { NotificationsButton } from '@/features/notifications'
import { useUiStore } from '@/stores/ui-store'
import { Button } from '@/components/ui/button'

import { Breadcrumbs } from './breadcrumbs'
import { UserMenu } from './user-menu'

/**
 * The application bar.
 *
 * Everything in it is either a way to move (the drawer trigger, the rail
 * toggle, the trail) or a way to reach something that belongs to the person
 * rather than to the screen (notifications, the account menu). Nothing about
 * the current screen's content is here; that belongs to the screen, through
 * `PageHeader`.
 *
 * Sticky, because the account menu and the sign-out inside it should not
 * require scrolling a long board back to the top to reach.
 */
export function AppHeader({ onOpenMobileNav }: { onOpenMobileNav: () => void }) {
  const collapsed = useUiStore((state) => state.sidebarCollapsed)
  const toggleSidebar = useUiStore((state) => state.toggleSidebar)

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b border-border bg-background/85 px-3 backdrop-blur-sm md:px-6">
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden"
        onClick={onOpenMobileNav}
        aria-label="Open the navigation"
      >
        <MenuIcon className="size-4" aria-hidden="true" />
      </Button>

      <Button
        variant="ghost"
        size="icon"
        className="hidden md:inline-flex"
        onClick={toggleSidebar}
        aria-label={collapsed ? 'Expand the navigation rail' : 'Collapse the navigation rail'}
        aria-pressed={collapsed}
      >
        <PanelLeftIcon className="size-4" aria-hidden="true" />
      </Button>

      <Breadcrumbs className="min-w-0" />

      <div className="ml-auto flex shrink-0 items-center gap-1">
        <NotificationsButton />
        <UserMenu />
      </div>
    </header>
  )
}
