import { DownloadIcon, PaperclipIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { toUserMessage } from '@/lib/api'

import { downloadAttachment } from '../api'
import { useAttachmentAbilities, useCollaborationMutations } from '../hooks'
import { formatBytes } from '../schemas'
import type { Attachment } from '../types'

/**
 * Files, as a row of chips. Used under a comment and in the Files panel.
 *
 * Downloading goes through the API client rather than a link. The endpoint
 * authorises on the bearer token, which is held in memory and never put on a
 * URL, so an anchor would arrive unauthenticated; fetching gives the browser
 * the bytes and a temporary object URL saves them under the right name.
 *
 * The object URL is revoked immediately after the click. Leaving it alive would
 * hold the whole file in memory for as long as the page is open, and would also
 * leave a URL that reaches the bytes without going back through the API.
 */

function useDownload() {
  const [busyId, setBusyId] = useState<string | null>(null)
  const workspace = useActiveWorkspace()

  const download = async (attachment: Attachment) => {
    if (!workspace) return
    setBusyId(attachment.id)
    try {
      const blob = await downloadAttachment(workspace.workspaceId, attachment.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = attachment.filename
      document.body.append(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast.error(toUserMessage(error))
    } finally {
      setBusyId(null)
    }
  }

  return { download, busyId }
}

function AttachmentChip({
  attachment,
  taskId,
  ownsProject,
  onDownload,
  downloading,
}: {
  attachment: Attachment
  taskId: string
  ownsProject: boolean
  onDownload: (attachment: Attachment) => void
  downloading: boolean
}) {
  const { removeAttachment } = useCollaborationMutations(taskId)
  const abilities = useAttachmentAbilities(attachment, ownsProject)

  return (
    <li className="flex items-center gap-1.5 rounded-md border border-border px-2 py-1 text-xs">
      <PaperclipIcon className="size-3 shrink-0" aria-hidden="true" />
      <span className="max-w-[14rem] truncate" title={attachment.filename}>
        {attachment.filename}
      </span>
      <span className="shrink-0 text-muted-foreground">{formatBytes(attachment.sizeBytes)}</span>

      <Button
        variant="ghost"
        size="icon-xs"
        disabled={downloading}
        aria-label={`Download ${attachment.filename}`}
        onClick={() => onDownload(attachment)}
      >
        <DownloadIcon aria-hidden="true" />
      </Button>

      {abilities.canDelete ? (
        <Button
          variant="ghost"
          size="icon-xs"
          disabled={removeAttachment.isPending}
          aria-label={`Remove ${attachment.filename}`}
          onClick={async () => {
            try {
              await removeAttachment.mutateAsync(attachment.id)
              toast.success('File removed.')
            } catch (error) {
              toast.error(toUserMessage(error))
            }
          }}
        >
          <Trash2Icon aria-hidden="true" />
        </Button>
      ) : null}
    </li>
  )
}

export function AttachmentChips({
  attachments,
  taskId,
  ownsProject,
}: {
  attachments: Attachment[]
  taskId: string
  ownsProject: boolean
}) {
  const { download, busyId } = useDownload()

  return (
    <ul className="flex flex-wrap gap-2">
      {attachments.map((attachment) => (
        <AttachmentChip
          key={attachment.id}
          attachment={attachment}
          taskId={taskId}
          ownsProject={ownsProject}
          onDownload={(file) => void download(file)}
          downloading={busyId === attachment.id}
        />
      ))}
    </ul>
  )
}
