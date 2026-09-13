import { PaperclipIcon } from 'lucide-react'
import { useRef } from 'react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { toUserMessage } from '@/lib/api'
import { relativeTime } from '@/lib/datetime'

import { useAttachments, useCollaborationMutations, useCollaborationPermissions } from '../hooks'
import { ACCEPTED_FILE_TYPES, formatBytes, MAX_FILE_BYTES, tooLarge } from '../schemas'
import type { Attachment } from '../types'
import { AttachmentChips } from './attachment-chips'

/**
 * Every file on the task, in one place.
 *
 * The listing includes files that belong to comments, which is what the
 * `commentId` on each record is for. They are shown here as well as under their
 * comment on purpose: somebody looking for a document should not have to
 * remember which message it arrived in.
 *
 * Uploading here attaches to the task itself rather than to any comment, which
 * is the state the backend calls an unclaimed file.
 */
export function AttachmentPanel({ taskId, ownsProject }: { taskId: string; ownsProject: boolean }) {
  const attachments = useAttachments(taskId)
  const { uploadAttachment } = useCollaborationMutations(taskId)
  const permissions = useCollaborationPermissions()
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const onFilesChosen = async (files: FileList | null) => {
    if (!files) return
    for (const file of Array.from(files)) {
      if (tooLarge(file)) {
        toast.error(`${file.name} is larger than ${formatBytes(MAX_FILE_BYTES)}.`)
        continue
      }
      try {
        await uploadAttachment.mutateAsync(file)
        toast.success(`${file.name} uploaded.`)
      } catch (error) {
        toast.error(toUserMessage(error))
      }
    }
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  if (attachments.isError) {
    return <ErrorState error={attachments.error} onRetry={() => void attachments.refetch()} />
  }

  if (attachments.isPending) {
    return (
      <div className="space-y-2" role="status" aria-live="polite">
        <span className="sr-only">Loading the files</span>
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-2/3" />
      </div>
    )
  }

  const files: Attachment[] = attachments.data
  const onTask = files.filter((file) => file.commentId === null)
  const onComments = files.filter((file) => file.commentId !== null)

  return (
    <div className="space-y-4">
      {permissions.canUpload ? (
        <>
          <Button
            variant="outline"
            size="sm"
            disabled={uploadAttachment.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            <PaperclipIcon aria-hidden="true" />
            {uploadAttachment.isPending ? 'Uploading…' : 'Upload a file'}
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

      {files.length === 0 ? (
        <EmptyState
          icon={PaperclipIcon}
          title="No files yet"
          description={
            permissions.canUpload
              ? `Up to ${formatBytes(MAX_FILE_BYTES)} each. Images, PDFs, archives, Office documents and plain text.`
              : 'Files attached to this task will appear here.'
          }
          className="border-0 px-0 py-6"
        />
      ) : (
        <div className="space-y-4">
          {onTask.length > 0 ? (
            <div className="space-y-1.5">
              <p className="text-xs text-muted-foreground">On the task</p>
              <AttachmentChips attachments={onTask} taskId={taskId} ownsProject={ownsProject} />
            </div>
          ) : null}

          {onComments.length > 0 ? (
            <div className="space-y-1.5">
              <p className="text-xs text-muted-foreground">From the discussion</p>
              <AttachmentChips attachments={onComments} taskId={taskId} ownsProject={ownsProject} />
            </div>
          ) : null}

          <p className="text-xs text-muted-foreground">
            {files.length} file{files.length === 1 ? '' : 's'}, most recent{' '}
            {relativeTime(
              files.reduce(
                (latest, file) => (file.createdAt > latest ? file.createdAt : latest),
                files[0]?.createdAt ?? '',
              ),
            )}
            .
          </p>
        </div>
      )}
    </div>
  )
}
