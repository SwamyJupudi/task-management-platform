import { BellIcon, LogOutIcon, MonitorIcon, MoonIcon, SettingsIcon, SunIcon, UserIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useLogout } from '@/features/auth'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { useSessionStore } from '@/stores/session-store'
import { useUiStore, type Theme } from '@/stores/ui-store'

const themeOptions: readonly { value: Theme; label: string; icon: typeof SunIcon }[] = [
  { value: 'system', label: 'System', icon: MonitorIcon },
  { value: 'light', label: 'Light', icon: SunIcon },
  { value: 'dark', label: 'Dark', icon: MoonIcon },
]

function isTheme(value: string): value is Theme {
  return themeOptions.some((option) => option.value === value)
}

function initials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase() || '?'
}

/**
 * The account menu: who is signed in, where their own settings are, the theme,
 * and the way out.
 *
 * The theme lives here rather than as a separate button in the header because
 * it is a preference about this account's browser, which is what the rest of
 * this menu is about, and because three named options are clearer than one
 * button that cycles silently through them.
 *
 * Signing out navigates nowhere of its own accord. `useLogout` revokes the
 * refresh token and clears the session, the store flips to `anonymous`, and
 * `RequireAuth` performs the redirect — one redirect, decided in one place.
 */
export function UserMenu() {
  const user = useSessionStore((state) => state.user)
  const platformRole = useSessionStore((state) => state.platformRole)
  const workspace = useActiveWorkspace()
  const theme = useUiStore((state) => state.theme)
  const setTheme = useUiStore((state) => state.setTheme)
  const logout = useLogout()

  if (!user) return null

  const fullName = `${user.firstName} ${user.lastName}`.trim()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="rounded-full"
          aria-label={`Account: ${fullName}`}
        >
          <Avatar size="sm">
            <AvatarFallback>{initials(user.firstName, user.lastName)}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-64">
        <div className="flex items-center gap-2 px-2 py-1.5">
          <Avatar>
            <AvatarFallback>{initials(user.firstName, user.lastName)}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{fullName}</p>
            <p className="truncate text-xs text-muted-foreground">{user.email}</p>
          </div>
        </div>

        {platformRole || workspace ? (
          <div className="flex flex-wrap gap-1 px-2 pb-2">
            {platformRole ? <Badge variant="secondary">{platformRole}</Badge> : null}
            {workspace ? <Badge variant="outline">{workspace.roleName}</Badge> : null}
          </div>
        ) : null}

        <DropdownMenuSeparator />

        {/* Outside the workspace block on purpose: an account exists whether or
            not this person belongs to a workspace, and somebody with no
            membership still needs their password and their sessions. */}
        <DropdownMenuItem asChild>
          <Link to={paths.account.root}>
            <UserIcon aria-hidden="true" />
            Your account
          </Link>
        </DropdownMenuItem>

        {workspace ? (
          <>
            <DropdownMenuItem asChild>
              <Link to={paths.workspace.notifications(workspace.workspaceSlug)}>
                <BellIcon aria-hidden="true" />
                Notifications
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to={paths.workspace.settings(workspace.workspaceSlug)}>
                <SettingsIcon aria-hidden="true" />
                Settings
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        ) : null}

        <DropdownMenuLabel>Theme</DropdownMenuLabel>
        <DropdownMenuRadioGroup
          value={theme}
          onValueChange={(value) => {
            if (isTheme(value)) setTheme(value)
          }}
        >
          {themeOptions.map((option) => (
            <DropdownMenuRadioItem key={option.value} value={option.value}>
              <option.icon aria-hidden="true" />
              {option.label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>

        <DropdownMenuSeparator />

        <DropdownMenuItem
          variant="destructive"
          disabled={logout.isPending}
          onSelect={() => logout.mutate()}
        >
          <LogOutIcon aria-hidden="true" />
          {logout.isPending ? 'Signing out…' : 'Sign out'}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
