import { BellIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'
import { useActiveWorkspaceSlug } from '@/hooks/use-active-workspace'

import { useUnreadNotificationCount } from '../hooks'

/** Three digits would not fit the dot, and the exact number stops mattering. */
const DISPLAY_CAP = 99

/**
 * The way into the notification feed, and the count of what is waiting.
 *
 * The three states are deliberately not three appearances. While the count is
 * loading there is no badge, and if the request fails there is still no badge:
 * a bell that cannot say how many are unread is exactly as useful as one that
 * says none, and an error toast for a number nobody asked for would be noise.
 * The count is announced separately from the icon so a screen reader reads
 * "Notifications, 3 unread" rather than a number with no context.
 */
export function NotificationsButton() {
  const slug = useActiveWorkspaceSlug()
  const { data: unread } = useUnreadNotificationCount()

  if (!slug) return null

  const count = unread ?? 0
  const label = count > 0 ? `Notifications, ${count} unread` : 'Notifications'

  return (
    <Button variant="ghost" size="icon" className="relative" asChild>
      <Link to={paths.workspace.notifications(slug)} aria-label={label}>
        <BellIcon className="size-4" aria-hidden="true" />
        {count > 0 ? (
          <span
            className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[0.625rem] font-semibold text-primary-foreground tabular-nums"
            aria-hidden="true"
          >
            {count > DISPLAY_CAP ? `${DISPLAY_CAP}+` : count}
          </span>
        ) : null}
      </Link>
    </Button>
  )
}
