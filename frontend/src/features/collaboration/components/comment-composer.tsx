import { zodResolver } from '@hookform/resolvers/zod'
import { AtSignIcon, PaperclipIcon, XIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Textarea } from '@/components/ui/textarea'
import { toUserMessage } from '@/lib/api'

import { useCollaborationMutations, useMentionCandidates } from '../hooks'
import { insertMention } from '../mentions'
import {
  ACCEPTED_FILE_TYPES,
  commentSchema,
  formatBytes,
  MAX_FILES_PER_COMMENT,
  MAX_FILE_BYTES,
  tooLarge,
  type CommentValues,
} from '../schemas'
import type { Attachment } from '../types'

/**
 * Writing a comment, with the people and the files that go in it.
 *
 * Files are uploaded the moment they are chosen rather than when the comment is
 * posted, because that is the order the API works in: a file is uploaded to the
 * task first and claimed by a comment afterwards, through `attachmentIds`. It
 * also means a slow upload does not hold up the writing, and that a failure is
 * reported against the file rather than against the comment.
 *
 * The consequence is worth stating: a file uploaded and then abandoned stays on
 * the task as an unclaimed attachment. That is the backend's model — an
 * unclaimed file simply belongs to the task rather than to a comment — so it
 * appears under Files rather than being lost.
 *
 * A mention is inserted as `@[user:<uuid>]`, the canonical form the backend
 * parses. The picker writes the token; nothing tries to guess a mention out of
 * free text, which would make the words and the notifications disagree.
 */
export function CommentComposer({
  taskId,
  projectId,
  canUpload,
}: {
  taskId: string
  projectId: string
  canUpload: boolean
}) {
  const { createComment, uploadAttachment } = useCollaborationMutations(taskId)
  const candidates = useMentionCandidates(projectId)

  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [pending, setPending] = useState<Attachment[]>([])

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<CommentValues>({
    resolver: zodResolver(commentSchema),
    defaultValues: { body: '' },
  })

  // `register` owns the ref, so the composer keeps its own alongside it to read
  // the caret position when a mention is inserted.
  const bodyField = register('body')

  const addMention = (userId: string) => {
    const element = textareaRef.current
    const current = getValues('body')
    const caret = element?.selectionStart ?? current.length

    const next = insertMention(current, caret, userId)
    setValue('body', next.body, { shouldValidate: true })

    // Put the caret after the token so typing continues naturally.
    requestAnimationFrame(() => {
      element?.focus()
      element?.setSelectionRange(next.caret, next.caret)
    })
  }

  const onFilesChosen = async (files: FileList | null) => {
    if (!files || files.length === 0) return

    for (const file of Array.from(files)) {
      if (pending.length >= MAX_FILES_PER_COMMENT) {
        toast.error(`A comment can carry at most ${MAX_FILES_PER_COMMENT} files.`)
        break
      }
      if (tooLarge(file)) {
        toast.error(`${file.name} is larger than ${formatBytes(MAX_FILE_BYTES)}.`)
        continue
      }
      try {
        const uploaded = await uploadAttachment.mutateAsync(file)
        setPending((current) => [...current, uploaded])
      } catch (error) {
        // Includes the type the backend refused after reading the real bytes,
        // which the extension filter cannot catch.
        toast.error(toUserMessage(error))
      }
    }

    // Cleared so choosing the same file twice still fires a change event.
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      await createComment.mutateAsync({
        body: values.body,
        ...(pending.length > 0 ? { attachmentIds: pending.map((file) => file.id) } : {}),
      })
      reset({ body: '' })
      setPending([])
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  })

  return (
    <form onSubmit={onSubmit} className="space-y-2" noValidate>
      <Textarea
        {...bodyField}
        ref={(element) => {
          bodyField.ref(element)
          textareaRef.current = element
        }}
        rows={3}
        placeholder="Write a comment. Use @ to mention somebody."
        aria-label="Write a comment"
        aria-invalid={errors.body ? true : undefined}
      />

      {errors.body ? (
        <p role="alert" className="text-xs text-destructive">
          {errors.body.message}
        </p>
      ) : null}

      {pending.length > 0 ? (
        <ul className="flex flex-wrap gap-2">
          {pending.map((file) => (
            <li
              key={file.id}
              className="flex items-center gap-1.5 rounded-md border border-border px-2 py-1 text-xs"
            >
              <PaperclipIcon className="size-3 shrink-0" aria-hidden="true" />
              <span className="max-w-[12rem] truncate">{file.filename}</span>
              <span className="text-muted-foreground">{formatBytes(file.sizeBytes)}</span>
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                aria-label={`Do not attach ${file.filename}`}
                // Only unlinks it from this comment. The file stays on the task,
                // which is what an unclaimed attachment is.
                onClick={() => setPending((current) => current.filter((f) => f.id !== file.id))}
              >
                <XIcon aria-hidden="true" />
              </Button>
            </li>
          ))}
        </ul>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" size="sm" disabled={isSubmitting}>
          {isSubmitting ? 'Posting…' : 'Comment'}
        </Button>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!candidates.data || candidates.data.length === 0}
            >
              <AtSignIcon aria-hidden="true" />
              Mention
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="max-h-72 w-56 overflow-y-auto">
            <DropdownMenuLabel>On this project</DropdownMenuLabel>
            {(candidates.data ?? []).map((person) => (
              <DropdownMenuItem key={person.userId} onSelect={() => addMention(person.userId)}>
                {person.firstName} {person.lastName}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        {canUpload ? (
          <>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={uploadAttachment.isPending}
              onClick={() => fileInputRef.current?.click()}
            >
              <PaperclipIcon aria-hidden="true" />
              {uploadAttachment.isPending ? 'Uploading…' : 'Attach'}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              hidden
              accept={ACCEPTED_FILE_TYPES}
              onChange={(event) => void onFilesChosen(event.target.files)}
            />
          </>
        ) : null}
      </div>
    </form>
  )
}
