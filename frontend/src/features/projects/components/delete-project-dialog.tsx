import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

import type { Project } from '../types'

/**
 * Confirms removing a project.
 *
 * Worth a confirmation rather than an undo: the delete is soft server-side, but
 * nothing in the interface can reverse it, and it frees the key and the name
 * for reuse — so a project deleted by accident cannot simply be recreated as it
 * was if something has since taken its key.
 *
 * The wording says what actually happens rather than "this cannot be undone",
 * which would be a guess about a backend that in fact keeps the row.
 */
export function DeleteProjectDialog({
  project,
  open,
  onOpenChange,
  onConfirm,
  pending,
}: {
  project: Project
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  pending: boolean
}) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete {project.name}?</AlertDialogTitle>
          <AlertDialogDescription>
            It disappears from every listing, and its key{' '}
            <span className="font-mono">{project.key}</span> and name become available for a new
            project. To shelve it instead and keep it readable, move it to Archived.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            disabled={pending}
            onClick={(event) => {
              // The dialog closes itself on action; the mutation decides when,
              // so that a failure leaves the dialog up with its message.
              event.preventDefault()
              onConfirm()
            }}
          >
            {pending ? 'Deleting…' : 'Delete project'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
