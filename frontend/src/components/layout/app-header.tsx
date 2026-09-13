import { MonitorIcon, MoonIcon, PanelLeftIcon, SunIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { env } from '@/config/env'
import { useSessionStore } from '@/stores/session-store'
import { useUiStore, type Theme } from '@/stores/ui-store'

const themeOrder: readonly Theme[] = ['system', 'light', 'dark']
const themeIcon = { system: MonitorIcon, light: SunIcon, dark: MoonIcon } as const

function ThemeToggle() {
  const theme = useUiStore((state) => state.theme)
  const setTheme = useUiStore((state) => state.setTheme)
  const Icon = themeIcon[theme]

  const next = themeOrder[(themeOrder.indexOf(theme) + 1) % themeOrder.length] ?? 'system'

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={() => setTheme(next)}
      aria-label={`Theme: ${theme}. Switch to ${next}.`}
    >
      <Icon className="size-4" aria-hidden="true" />
    </Button>
  )
}

/**
 * The application bar.
 *
 * The workspace name comes from the session's membership list, so the header
 * needs no request of its own. The account menu and the workspace switcher
 * are left to the features that own them.
 */
export function AppHeader() {
  const toggleSidebar = useUiStore((state) => state.toggleSidebar)
  const memberships = useSessionStore((state) => state.memberships)
  const activeWorkspaceId = useSessionStore((state) => state.activeWorkspaceId)

  const workspace = memberships.find((m) => m.workspaceId === activeWorkspaceId)

  return (
    <header className="bg-background border-border sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <Button
        variant="ghost"
        size="icon"
        className="hidden md:inline-flex"
        onClick={toggleSidebar}
        aria-label="Toggle the navigation rail"
      >
        <PanelLeftIcon className="size-4" aria-hidden="true" />
      </Button>

      <span className="text-sm font-semibold">{env.appName}</span>

      {workspace ? (
        <>
          <Separator orientation="vertical" className="mx-1 h-5" />
          <span className="text-muted-foreground truncate text-sm">{workspace.workspaceName}</span>
        </>
      ) : null}

      <div className="ml-auto flex items-center gap-1">
        <ThemeToggle />
      </div>
    </header>
  )
}
