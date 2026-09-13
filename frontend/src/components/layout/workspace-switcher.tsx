import { CheckIcon, ChevronsUpDownIcon } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { cn } from '@/lib/utils'
import { useSessionStore } from '@/stores/session-store'

/** Two letters from the workspace name, for the square beside it. */
function monogram(name: string): string {
  const words = name.trim().split(/\s+/)
  const letters =
    words.length > 1 ? `${words[0]?.[0] ?? ''}${words[1]?.[0] ?? ''}` : name.slice(0, 2)
  return letters.toUpperCase()
}

function WorkspaceMark({ name, className }: { name: string; className?: string }) {
  return (
    <span
      className={cn(
        'flex size-7 shrink-0 items-center justify-center rounded-md bg-sidebar-primary text-xs font-semibold text-sidebar-primary-foreground',
        className,
      )}
      aria-hidden="true"
    >
      {monogram(name)}
    </span>
  )
}

/**
 * Chooses which workspace the interface is showing.
 *
 * The list is the membership list `/auth/me` already returned, so this makes
 * no request. `GET /workspaces` exists but is gated on the platform permission
 * `workspace:read`, which an ordinary employee does not hold: asking it here
 * would give most of the company an empty switcher and a 403 in the console.
 *
 * Selecting navigates rather than writing to the store. `WorkspaceRoute` reads
 * the slug back out of the URL and sets the active workspace from it, so there
 * is exactly one way the active workspace changes however the user got here —
 * this menu, a pasted link or the back button.
 */
export function WorkspaceSwitcher({ collapsed = false }: { collapsed?: boolean }) {
  const memberships = useSessionStore((state) => state.memberships)
  const active = useActiveWorkspace()
  const navigate = useNavigate()

  if (!active) return null

  // Nothing to switch to. A menu with one disabled entry in it is a worse
  // answer than a label.
  if (memberships.length === 1) {
    return (
      <div
        className={cn(
          'flex h-10 items-center gap-2 rounded-md px-2',
          collapsed && 'justify-center px-0',
        )}
      >
        <WorkspaceMark name={active.workspaceName} />
        {collapsed ? null : (
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{active.workspaceName}</span>
            <span className="block truncate text-xs text-muted-foreground">{active.roleName}</span>
          </span>
        )}
      </div>
    )
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className={cn('h-10 w-full justify-start gap-2 px-2', collapsed && 'justify-center px-0')}
          aria-label={`Workspace: ${active.workspaceName}. Switch workspace.`}
        >
          <WorkspaceMark name={active.workspaceName} />
          {collapsed ? null : (
            <>
              <span className="min-w-0 flex-1 text-left">
                <span className="block truncate text-sm font-medium">{active.workspaceName}</span>
                <span className="block truncate text-xs font-normal text-muted-foreground">
                  {active.roleName}
                </span>
              </span>
              <ChevronsUpDownIcon className="size-4 text-muted-foreground" aria-hidden="true" />
            </>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="start" className="w-64">
        <DropdownMenuLabel>Your workspaces</DropdownMenuLabel>
        {memberships.map((membership) => {
          const current = membership.workspaceId === active.workspaceId
          return (
            <DropdownMenuItem
              key={membership.workspaceId}
              onSelect={() => {
                if (current) return
                // The dashboard rather than the equivalent screen in the new
                // workspace: the record ids in the current path belong to the
                // workspace being left and resolve to nothing in the next one.
                void navigate(paths.workspace.dashboard(membership.workspaceSlug))
              }}
            >
              <WorkspaceMark name={membership.workspaceName} className="size-6 text-[0.625rem]" />
              <span className="min-w-0 flex-1">
                <span className="block truncate">{membership.workspaceName}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {membership.roleName}
                </span>
              </span>
              {current ? <CheckIcon className="size-4 shrink-0" aria-hidden="true" /> : null}
            </DropdownMenuItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
