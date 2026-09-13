import { z } from 'zod'

/**
 * Client-side validation for the comment forms, and the upload bounds.
 *
 * Every rule mirrors a constraint the backend enforces, so the browser refuses
 * exactly what the server would and nothing more. This is a convenience, not a
 * control: the server validates again regardless, and a rejected value comes
 * back on the field that caused it.
 */

/** `@NotBlank @Size(max = 5000)` on both the create and the update record. */
const BODY_MAX = 5000

const body = z
  .string()
  .trim()
  .min(1, 'Write something first.')
  .max(BODY_MAX, `A comment cannot be longer than ${BODY_MAX.toLocaleString()} characters.`)

export const commentSchema = z.object({ body })

export type CommentValues = z.infer<typeof commentSchema>

/** `@Size(max = 10)` on `attachmentIds`: how many files one comment may claim. */
export const MAX_FILES_PER_COMMENT = 10

/** `app.storage.max-file-size`, 10MB by default. */
export const MAX_FILE_BYTES = 10 * 1024 * 1024

/**
 * What `ContentTypes` accepts, by extension.
 *
 * Only a hint. The backend detects the real type from the file's own bytes and
 * ignores both the extension and whatever the browser claimed, so this narrows
 * the file picker and catches the obvious mistake early — it decides nothing.
 */
export const ACCEPTED_FILE_TYPES = '.png,.jpg,.jpeg,.gif,.webp,.pdf,.zip,.docx,.xlsx,.pptx,.txt'

/** Human-readable sizes, for the list and for the too-large message. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** The one check worth making before a 10MB upload leaves the browser. */
export function tooLarge(file: File): boolean {
  return file.size > MAX_FILE_BYTES
}
