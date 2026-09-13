import { useQuery, type UseQueryResult } from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { queryKeys } from '@/lib/query-client'

import * as notificationsApi from './api'

/**
 * How many notifications are waiting in the active workspace.
 *
 * Polled rather than pushed. The backend's delivery is a paged feed and a
 * count; server-sent events are a later swap that needs no change to the
 * response shape, and therefore none here either — only the `refetchInterval`
 * goes away.
 *
 * The interval is a minute, which is often enough for a badge and cheap enough
 * that a tab left open all day is not a problem. It does not refetch while the
 * tab is hidden, which is TanStack Query's default.
 */
export function useUnreadNotificationCount(): UseQueryResult<number> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return useQuery({
    queryKey: [...queryKeys.notifications, 'unread-count', workspaceId],
    queryFn: () => notificationsApi.unreadCount(workspaceId as string),
    enabled: workspaceId !== null,
    select: (data) => data.unread,
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
}
