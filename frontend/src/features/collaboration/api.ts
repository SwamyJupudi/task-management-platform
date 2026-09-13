import { api, type Page } from '@/lib/api'
import type { ActivityEntry } from '@/types/activity'

import type {
  Attachment,
  Comment,
  CreateCommentInput,
  MentionCandidate,
  UpdateCommentInput,
} from './types'

/**
 * Comments, files and history on a task.
 *
 * Written and read under the task, addressed flat afterwards — which is the
 * backend's own arrangement, and the reason is worth keeping in mind here: a
 * link from a notification should not have to carry the task to reach the
 * comment it is about. So creating is `/tasks/{id}/comments` and editing is
 * `/comments/{id}`.
 *
 * No permission family gates reading any of this. A comment is visible exactly
 * when its task is, and a task's history needs nothing beyond being able to see
 * the task — browsing the whole workspace's audit trail is the administrator's
 * endpoint and is not used here.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}`

// --- comments ---------------------------------------------------------------

/** `GET /tasks/{taskId}/comments`. Oldest first, paged. */
export function listComments(
  workspaceId: string,
  taskId: string,
  page: number,
  size: number,
): Promise<Page<Comment>> {
  return api.get<Page<Comment>>(`${base(workspaceId)}/tasks/${taskId}/comments`, {
    params: { page, size },
  })
}

/**
 * `POST /tasks/{taskId}/comments`.
 *
 * Anybody who can see the task may do this. The narrowing that restricts
 * editing a task to its assignee, reporter, project owner or team lead is
 * deliberately not applied by the backend here: somebody on a project who holds
 * none of those can still take part in the discussion, which is the point of
 * one.
 */
export function createComment(
  workspaceId: string,
  taskId: string,
  body: CreateCommentInput,
): Promise<Comment> {
  return api.post<Comment>(`${base(workspaceId)}/tasks/${taskId}/comments`, body)
}

/** `PATCH /comments/{id}`. Author-only, whatever else the caller holds. */
export function updateComment(
  workspaceId: string,
  commentId: string,
  body: UpdateCommentInput,
): Promise<Comment> {
  return api.patch<Comment>(`${base(workspaceId)}/comments/${commentId}`, body)
}

/** `DELETE /comments/{id}`. Soft delete; its attachments go with it. */
export function deleteComment(workspaceId: string, commentId: string): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/comments/${commentId}`)
}

// --- attachments ------------------------------------------------------------

/** `GET /tasks/{taskId}/attachments`. Includes the files on its comments. */
export function listAttachments(workspaceId: string, taskId: string): Promise<Attachment[]> {
  return api.get<Attachment[]>(`${base(workspaceId)}/tasks/${taskId}/attachments`)
}

/**
 * `POST /tasks/{taskId}/attachments`, multipart.
 *
 * The client sets no `Content-Type` for a `FormData` body, because the browser
 * has to supply the multipart boundary itself. The backend detects the real
 * type from the file's own bytes and ignores whatever the client claimed.
 */
export function uploadAttachment(
  workspaceId: string,
  taskId: string,
  file: File,
): Promise<Attachment> {
  const form = new FormData()
  form.append('file', file)
  return api.post<Attachment>(`${base(workspaceId)}/tasks/${taskId}/attachments`, form)
}

/** `DELETE /attachments/{id}`. Soft delete; the stored bytes await the purge. */
export function deleteAttachment(workspaceId: string, attachmentId: string): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/attachments/${attachmentId}`)
}

/**
 * Downloads the bytes of one file.
 *
 * A plain anchor cannot do this: the endpoint authorises on the bearer token,
 * which is held in memory and never on the URL, so the link would arrive
 * unauthenticated. Fetching through the shared client carries the token, gets
 * the same retry on an expired session as everything else, and hands back a
 * blob the caller saves.
 *
 * The response is always `Content-Disposition: attachment` with `nosniff`, so
 * nothing here has to decide whether a file is safe to render — it is never
 * rendered.
 */
export async function downloadAttachment(workspaceId: string, attachmentId: string): Promise<Blob> {
  const response = await api.raw(`${base(workspaceId)}/attachments/${attachmentId}/content`)
  return response.blob()
}

// --- activity ---------------------------------------------------------------

/**
 * `GET /tasks/{taskId}/activity`. Newest first, paged.
 *
 * Includes the history of the task's subtasks, comments and attachments, which
 * is why this is the one history worth showing beside a task. Reading it needs
 * nothing beyond being able to see the task: a history nobody on the project
 * could read would make the project's own past invisible to the people working
 * on it.
 */
export function listTaskActivity(
  workspaceId: string,
  taskId: string,
  page: number,
  size: number,
): Promise<Page<ActivityEntry>> {
  return api.get<Page<ActivityEntry>>(`${base(workspaceId)}/tasks/${taskId}/activity`, {
    params: { page, size },
  })
}

// --- lookups ----------------------------------------------------------------

/**
 * The task's project members, for the mention picker.
 *
 * The same lookup the tasks and projects features each keep their own copy of.
 * Three copies is one too many and they should be consolidated into a shared
 * module; that is a refactor across three shipped features rather than part of
 * this one, so it is recorded here rather than done quietly.
 */
export async function listMentionCandidates(
  workspaceId: string,
  projectId: string,
): Promise<MentionCandidate[]> {
  const page = await api.get<Page<MentionCandidate>>(
    `${base(workspaceId)}/projects/${projectId}/members`,
    { params: { page: 0, size: 100 } },
  )
  return page.content
}
