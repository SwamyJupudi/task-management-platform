import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'
import { useSessionStore } from '@/stores/session-store'
import type { ActivityEntry } from '@/types/activity'

import * as collaborationApi from './api'
import type { Attachment, Comment, MentionCandidate } from './types'

/**
 * The collaboration feature's server state.
 *
 * Every key carries the workspace id, for the reason every other feature's
 * does: two workspaces are two different answers, and a key that left it out
 * would serve one workspace's discussion under the other's name.
 *
 * The thread and the history are paged oldest-first and newest-first
 * respectively, and both use an infinite query rather than page controls. A
 * discussion is read as one column of text, so "show more" belongs at the end
 * of it rather than a pager that throws away what is already on screen.
 */

const STALE_MS = 15_000
const COMMENT_PAGE_SIZE = 20
const ACTIVITY_PAGE_SIZE = 30

/** Everything about one task's collaboration, for a broad invalidation. */
function collaborationRoot(workspaceId: string, taskId: string) {
  return [...queryKeys.tasks, workspaceId, 'collaboration', taskId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may do in a discussion.
 *
 * Codes, never role names. Note that `comment:manage_any` deliberately does not
 * widen editing: an administrator may remove somebody's words, but nobody may
 * rewrite them and leave them attributed to their author. That rule is the
 * backend's and is mirrored rather than softened here.
 */
export interface CollaborationPermissions {
  canComment: boolean
  canEditOwn: boolean
  canDelete: boolean
  commentManageAny: boolean
  canUpload: boolean
  canDeleteAttachment: boolean
  attachmentManageAny: boolean
}

export function useCollaborationPermissions(): CollaborationPermissions {
  const { has } = usePermissions()

  return {
    canComment: has('comment:create'),
    canEditOwn: has('comment:update'),
    canDelete: has('comment:delete'),
    commentManageAny: has('comment:manage_any'),
    canUpload: has('attachment:create'),
    canDeleteAttachment: has('attachment:delete'),
    attachmentManageAny: has('attachment:manage_any'),
  }
}

/**
 * Whether the caller may edit or remove one particular comment.
 *
 * Editing is the author and nobody else, whatever they hold. Removing is the
 * author, the project owner, its team lead, or a holder of
 * `comment:manage_any`.
 *
 * The team-lead case needs the workspace's teams and is not answered here, so a
 * lead who is neither the author nor the project owner sees the control hidden
 * even though the API would allow it. That errs toward offering less than the
 * person has, and the backend remains the only thing that decides.
 */
export function useCommentAbilities(comment: Comment | undefined, ownsProject = false) {
  const permissions = useCollaborationPermissions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  const isAuthor = userId !== null && comment?.authorUserId === userId

  return {
    canEdit: permissions.canEditOwn && isAuthor,
    canDelete: permissions.canDelete && (isAuthor || permissions.commentManageAny || ownsProject),
  }
}

/** The same shape for a file: its uploader, `manage_any`, or the project owner. */
export function useAttachmentAbilities(attachment: Attachment | undefined, ownsProject = false) {
  const permissions = useCollaborationPermissions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  const isUploader = userId !== null && attachment?.uploaderUserId === userId

  return {
    canDelete:
      permissions.canDeleteAttachment &&
      (isUploader || permissions.attachmentManageAny || ownsProject),
  }
}

// --- queries ----------------------------------------------------------------

/** The thread, oldest first, a page at a time. */
export function useComments(taskId: string) {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return useInfiniteQuery({
    queryKey: [...collaborationRoot(workspaceId ?? 'none', taskId), 'comments'],
    queryFn: ({ pageParam }) =>
      collaborationApi.listComments(
        workspaceId as string,
        taskId,
        pageParam as number,
        COMMENT_PAGE_SIZE,
      ),
    initialPageParam: 0,
    // `last` is the server's own answer, read rather than derived from the
    // page index and the total, which would be a second opinion on it.
    getNextPageParam: (lastPage: Page<Comment>) => (lastPage.last ? undefined : lastPage.page + 1),
    enabled: workspaceId !== null,
    staleTime: STALE_MS,
  })
}

/** Every file on the task, including the ones hanging off its comments. */
export function useAttachments(taskId: string): UseQueryResult<Attachment[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return useQuery({
    queryKey: [...collaborationRoot(workspaceId ?? 'none', taskId), 'attachments'],
    queryFn: () => collaborationApi.listAttachments(workspaceId as string, taskId),
    enabled: workspaceId !== null,
    staleTime: STALE_MS,
  })
}

/** The task's history, newest first, a page at a time. */
export function useTaskActivity(taskId: string) {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return useInfiniteQuery({
    queryKey: [...collaborationRoot(workspaceId ?? 'none', taskId), 'activity'],
    queryFn: ({ pageParam }) =>
      collaborationApi.listTaskActivity(
        workspaceId as string,
        taskId,
        pageParam as number,
        ACTIVITY_PAGE_SIZE,
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage: Page<ActivityEntry>) =>
      lastPage.last ? undefined : lastPage.page + 1,
    enabled: workspaceId !== null,
    staleTime: STALE_MS,
  })
}

/** The task's project members, for the mention picker. */
export function useMentionCandidates(
  projectId: string | undefined,
): UseQueryResult<MentionCandidate[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { has } = usePermissions()

  return useQuery({
    queryKey: [...queryKeys.projects, workspaceId, 'mention-candidates', projectId],
    queryFn: () =>
      collaborationApi.listMentionCandidates(workspaceId as string, projectId as string),
    enabled: workspaceId !== null && projectId !== undefined && has('project:read'),
    staleTime: 5 * 60_000,
  })
}

// --- mutations --------------------------------------------------------------

/**
 * Every collaboration write, sharing one invalidation.
 *
 * Broad on purpose, and wider than it first looks: posting a comment with a
 * file attached changes the thread and the attachment listing, and every one of
 * these writes adds a row to the task's history. Invalidating the task's whole
 * collaboration subtree keeps the three panels agreeing with each other.
 */
export function useCollaborationMutations(taskId: string) {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId as string

  const invalidate = () => {
    if (!workspaceId) return
    void queryClient.invalidateQueries({ queryKey: collaborationRoot(workspaceId, taskId) })
  }

  const createComment = useMutation({
    mutationFn: (input: { body: string; attachmentIds?: string[] }) =>
      collaborationApi.createComment(workspaceId, taskId, input),
    onSuccess: invalidate,
  })

  const updateComment = useMutation({
    mutationFn: ({ commentId, body }: { commentId: string; body: string }) =>
      collaborationApi.updateComment(workspaceId, commentId, { body }),
    onSuccess: invalidate,
  })

  const removeComment = useMutation({
    mutationFn: (commentId: string) => collaborationApi.deleteComment(workspaceId, commentId),
    onSuccess: invalidate,
  })

  const uploadAttachment = useMutation({
    mutationFn: (file: File) => collaborationApi.uploadAttachment(workspaceId, taskId, file),
    onSuccess: invalidate,
  })

  const removeAttachment = useMutation({
    mutationFn: (attachmentId: string) =>
      collaborationApi.deleteAttachment(workspaceId, attachmentId),
    onSuccess: invalidate,
  })

  return {
    createComment,
    updateComment,
    removeComment,
    uploadAttachment,
    removeAttachment,
    workspaceId,
  }
}
