import { MessageSquareIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { toUserMessage } from '@/lib/api'
import { relativeTime } from '@/lib/datetime'

import {
  useCollaborationMutations,
  useCollaborationPermissions,
  useComments,
  useCommentAbilities,
} from '../hooks'
import type { Attachment, Comment } from '../types'
import { AttachmentChips } from './attachment-chips'
import { CommentBody } from './comment-body'
import { CommentComposer } from './comment-composer'

/**
 * The discussion on a task.
 *
 * Oldest first, which is how a conversation reads, with "show more" at the
 * bottom rather than a pager: paging a thread throws away the part somebody has
 * already read.
 *
 * Editing is the author's alone, whatever else the caller holds — an
 * administrator may remove somebody's words but may not rewrite them and leave
 * them attributed. That is the backend's rule and the controls mirror it rather
 * than softening it into one "can manage" flag.
 */

function initials(name: string | null): string {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const second = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return `${first}${second}`.toUpperCase() || '?'
}

function CommentItem({
  comment,
  taskId,
  attachments,
  ownsProject,
}: {
  comment: Comment
  taskId: string
  /** The files claimed by this comment, from the task's attachment listing. */
  attachments: Attachment[]
  ownsProject: boolean
}) {
  const { updateComment, removeComment } = useCollaborationMutations(taskId)
  const abilities = useCommentAbilities(comment, ownsProject)

  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(comment.body)

  const save = async () => {
    const trimmed = draft.trim()
    if (trimmed === '' || trimmed === comment.body) {
      setDraft(comment.body)
      setEditing(false)
      return
    }
    try {
      await updateComment.mutateAsync({ commentId: comment.id, body: trimmed })
      setEditing(false)
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  }

  return (
    <li className="flex gap-3 py-3">
      <Avatar size="sm" className="mt-0.5 shrink-0">
        <AvatarFallback>{initials(comment.authorName)}</AvatarFallback>
      </Avatar>

      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <span className="text-sm font-medium">{comment.authorName ?? 'Unknown'}</span>
          <time
            dateTime={comment.createdAt}
            title={new Date(comment.createdAt).toLocaleString()}
            className="text-xs text-muted-foreground"
          >
            {relativeTime(comment.createdAt)}
          </time>
          {comment.edited ? (
            <Badge variant="outline" className="text-[0.625rem]">
              edited
            </Badge>
          ) : null}

          <span className="ml-auto flex items-center gap-0.5">
            {abilities.canEdit && !editing ? (
              <Button
                variant="ghost"
                size="icon-xs"
                aria-label="Edit this comment"
                onClick={() => {
                  setDraft(comment.body)
                  setEditing(true)
                }}
              >
                <PencilIcon aria-hidden="true" />
              </Button>
            ) : null}

            {abilities.canDelete ? (
              <Button
                variant="ghost"
                size="icon-xs"
                aria-label="Remove this comment"
                disabled={removeComment.isPending}
                onClick={async () => {
                  try {
                    await removeComment.mutateAsync(comment.id)
                    toast.success('Comment removed.')
                  } catch (error) {
                    toast.error(toUserMessage(error))
                  }
                }}
              >
                <Trash2Icon aria-hidden="true" />
              </Button>
            ) : null}
          </span>
        </div>

        {editing ? (
          <div className="space-y-2">
            <Textarea
              value={draft}
              rows={3}
              autoFocus
              aria-label="Edit your comment"
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  setDraft(comment.body)
                  setEditing(false)
                }
              }}
            />
            <div className="flex gap-2">
              <Button size="sm" disabled={updateComment.isPending} onClick={() => void save()}>
                {updateComment.isPending ? 'Saving…' : 'Save'}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setDraft(comment.body)
                  setEditing(false)
                }}
              >
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <CommentBody body={comment.body} mentions={comment.mentions} />
        )}

        {attachments.length > 0 ? (
          <AttachmentChips attachments={attachments} taskId={taskId} ownsProject={ownsProject} />
        ) : null}
      </div>
    </li>
  )
}

export function CommentThread({
  taskId,
  projectId,
  attachments,
  ownsProject,
}: {
  taskId: string
  projectId: string
  /** The task's whole attachment listing; each comment takes its own from it. */
  attachments: Attachment[]
  ownsProject: boolean
}) {
  const thread = useComments(taskId)
  const permissions = useCollaborationPermissions()

  if (thread.isError) {
    return <ErrorState error={thread.error} onRetry={() => void thread.refetch()} />
  }

  if (thread.isPending) {
    return (
      <div className="space-y-3" role="status" aria-live="polite">
        <span className="sr-only">Loading the discussion</span>
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    )
  }

  const comments = thread.data.pages.flatMap((page) => page.content)
  const byComment = new Map<string, Attachment[]>()
  for (const file of attachments) {
    if (file.commentId === null) continue
    byComment.set(file.commentId, [...(byComment.get(file.commentId) ?? []), file])
  }

  return (
    <div className="space-y-4">
      {comments.length === 0 ? (
        <EmptyState
          icon={MessageSquareIcon}
          title="No comments yet"
          description={
            permissions.canComment
              ? 'Start the discussion, or mention somebody to bring them in.'
              : 'Discussion on this task will appear here.'
          }
          className="border-0 px-0 py-6"
        />
      ) : (
        <>
          {thread.hasNextPage ? (
            <Button
              variant="outline"
              size="sm"
              disabled={thread.isFetchingNextPage}
              onClick={() => void thread.fetchNextPage()}
            >
              {thread.isFetchingNextPage ? 'Loading…' : 'Show earlier comments'}
            </Button>
          ) : null}

          <ul className="divide-y divide-border">
            {comments.map((comment) => (
              <CommentItem
                key={comment.id}
                comment={comment}
                taskId={taskId}
                attachments={byComment.get(comment.id) ?? []}
                ownsProject={ownsProject}
              />
            ))}
          </ul>
        </>
      )}

      {permissions.canComment ? (
        <CommentComposer taskId={taskId} projectId={projectId} canUpload={permissions.canUpload} />
      ) : (
        <p className="text-xs text-muted-foreground">
          Commenting needs the comment:create permission in this workspace.
        </p>
      )}
    </div>
  )
}
