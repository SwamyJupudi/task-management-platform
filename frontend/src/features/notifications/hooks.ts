import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'

import * as notificationsApi from './api'
import type { Notification } from './types'

/** How many rows one page of the feed carries. */
const FEED_PAGE_SIZE = 30

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

/**
 * The feed, newest first, a page at a time.
 *
 * An infinite query rather than page controls: a feed is read as one column
 * downward, and a pager would throw away what has already been scrolled past.
 *
 * Keyed on the workspace and on whether it is narrowed to unread, so switching
 * the filter is a different question rather than a refetch of the same one.
 */
export function useNotificationFeed(unreadOnly: boolean) {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return useInfiniteQuery({
    queryKey: [...queryKeys.notifications, 'feed', workspaceId, unreadOnly ? 'unread' : 'all'],
    queryFn: ({ pageParam }) =>
      notificationsApi.listNotifications(
        workspaceId as string,
        unreadOnly,
        pageParam as number,
        FEED_PAGE_SIZE,
      ),
    initialPageParam: 0,
    // `last` is the server's own answer, read rather than derived from the page
    // index and the total, which would be a second opinion on it.
    getNextPageParam: (lastPage: Page<Notification>) =>
      lastPage.last ? undefined : lastPage.page + 1,
    enabled: workspaceId !== null,
    staleTime: 15_000,
  })
}

/**
 * Marking one read, and marking everything read.
 *
 * Both invalidate the whole notifications root, which is wider than the rows
 * they touched: the badge in the header reads the same data from a different
 * query, and a feed that updated without the badge following would be the kind
 * of disagreement people notice immediately.
 */
export function useNotificationMutations() {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId as string

  const invalidate = () => {
    if (!workspaceId) return
    void queryClient.invalidateQueries({ queryKey: queryKeys.notifications })
  }

  const markRead = useMutation({
    mutationFn: (notificationId: string) => notificationsApi.markRead(workspaceId, notificationId),
    onSuccess: invalidate,
  })

  const markAllRead = useMutation({
    mutationFn: () => notificationsApi.markAllRead(workspaceId),
    onSuccess: invalidate,
  })

  return { markRead, markAllRead }
}
