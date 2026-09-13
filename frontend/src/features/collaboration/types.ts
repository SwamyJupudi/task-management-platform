/**
 * The wire shapes the collaboration endpoints exchange.
 *
 * Each mirrors a record in the Spring `comments.dto` or `attachments.dto`
 * package. The audit entry these screens also show lives in `types/activity.ts`
 * instead, because more than one feature reads it.
 */

/**
 * One person named in a comment, resolved by the backend.
 *
 * Nothing about a person is stored in the body — only their identifier — so a
 * rename shows up the next time the comment is read rather than leaving a stale
 * name in the text. That is why the name arrives here rather than being parsed
 * out of what was typed.
 */
export interface Mention {
  userId: string
  email: string
  name: string
}

/**
 * One comment on a task.
 *
 * `body` is plain text and never HTML — the backend says so explicitly, and it
 * is rendered as text rather than interpreted.
 *
 * Attachments are deliberately absent from this record. A file carries the
 * comment it belongs to, so the task's attachment listing is what groups files
 * under comments; the comment module knows nothing about files.
 */
export interface Comment {
  id: string
  workspaceId: string
  projectId: string
  taskId: string
  authorUserId: string
  authorEmail: string | null
  authorName: string | null
  /** Plain text. Mentions appear inside it as `@[user:<uuid>]`. */
  body: string
  /** True once the author has changed it since writing it. */
  edited: boolean
  editedAt: string | null
  /** Everybody named in the body, resolved to current names. */
  mentions: Mention[]
  createdAt: string
  updatedAt: string
}

/** The body of `POST /tasks/{taskId}/comments`. */
export interface CreateCommentInput {
  /** Plain text; mention somebody by including `@[user:<uuid>]`. */
  body: string
  /**
   * Files already uploaded to this task by this caller and not yet claimed.
   *
   * Files arrive through the upload endpoint first: mixing a file part and a
   * JSON part in one request is awkward for every client and makes a partial
   * failure ambiguous.
   */
  attachmentIds?: string[] | undefined
}

/** The body of `PATCH /comments/{id}`. Required, because an edit changes words. */
export interface UpdateCommentInput {
  body: string
}

/**
 * One stored file.
 *
 * `commentId` is what lets a client group a task's files under the comments
 * they belong to. A file uploaded to the task itself rather than to a comment
 * has none.
 *
 * The storage key is deliberately absent from the contract; downloads go
 * through `/attachments/{id}/content`, which authorises first.
 */
export interface Attachment {
  id: string
  workspaceId: string
  projectId: string
  taskId: string
  commentId: string | null
  uploaderUserId: string
  uploaderEmail: string | null
  uploaderName: string | null
  filename: string
  /** Detected from the file's own bytes, not from what the client claimed. */
  contentType: string
  sizeBytes: number
  checksumSha256: string
  createdAt: string
}

/** Somebody on the task's project, for the mention picker. */
export interface MentionCandidate {
  userId: string
  email: string
  firstName: string
  lastName: string
}
