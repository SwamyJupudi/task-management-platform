import { zodResolver } from '@hookform/resolvers/zod'
import { CircleCheckIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { Field } from '@/components/common/field'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FormError } from '@/features/auth'
import { toUserMessage } from '@/lib/api'
import { applyFieldErrors } from '@/lib/form'
import { useSessionStore } from '@/stores/session-store'

import { useWorkspaceMutations } from '../hooks'
import { createWorkspaceSchema, suggestSlug, type CreateWorkspaceValues } from '../schemas'
import type { WorkspaceSummary } from '../types'

/**
 * Creating a workspace, and the thing that happens next.
 *
 * **Creating a workspace does not put you in it.** The endpoint writes the
 * workspace, seeds its three roles and sets a default role, and writes no
 * membership row for anybody — so the new workspace does not appear in
 * `/auth/me`, and `/w/{slug}` answers "workspace not found" to its own creator.
 * That is the backend's behaviour rather than an oversight on this side, and
 * hiding it would leave somebody clicking a link that cannot work.
 *
 * So the dialog has two steps. The first creates; the second offers the one
 * route in that uses an endpoint that already exists — invite your own address,
 * then accept the invitation from the link. Skipping it is fine: a platform
 * administrator can already administer the workspace from this panel without
 * being a member, and somebody else can be invited instead.
 *
 * The slug is suggested from the name and stays editable. This is the only
 * moment it can be chosen — no endpoint changes one afterwards, because a slug
 * appears in links that may already have been shared.
 */
export function CreateWorkspaceDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { create, inviteSelf } = useWorkspaceMutations()
  const email = useSessionStore((state) => state.user?.email ?? null)

  const [created, setCreated] = useState<WorkspaceSummary | null>(null)
  const [invited, setInvited] = useState(false)

  const {
    control,
    register,
    handleSubmit,
    setError,
    setValue,
    reset,
    formState: { errors, isSubmitting, dirtyFields },
  } = useForm<CreateWorkspaceValues>({
    resolver: zodResolver(createWorkspaceSchema),
    defaultValues: { name: '', slug: '' },
  })

  // `useWatch` rather than `watch`, which returns a new object on every render
  // and makes this component re-render itself in a loop.
  const name = useWatch({ control, name: 'name' })

  // The suggestion follows the name until somebody types an address of their
  // own. `setValue` without `shouldDirty` leaves the field clean, so the first
  // keystroke in it — which register's own handler marks dirty — is what stops
  // the suggestion for good.
  const slugTouched = dirtyFields.slug === true
  useEffect(() => {
    if (!slugTouched) setValue('slug', suggestSlug(name ?? ''))
  }, [name, slugTouched, setValue])

  const close = () => {
    onOpenChange(false)
    // Reset after the dialog has gone rather than under it.
    setTimeout(() => {
      reset({ name: '', slug: '' })
      setCreated(null)
      setInvited(false)
    }, 150)
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      const workspace = await create.mutateAsync({ name: values.name, slug: values.slug })
      toast.success(`${workspace.name} was created.`)
      setCreated(workspace)
    } catch (error) {
      applyFieldErrors(error, setError, ['name', 'slug'])
    }
  })

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(true) : close())}>
      <DialogContent className="sm:max-w-md">
        {created ? (
          <>
            <DialogHeader>
              <DialogTitle>{created.name} was created</DialogTitle>
              <DialogDescription>
                Its roles are seeded and new members will join as {created.defaultRoleSlug}.
              </DialogDescription>
            </DialogHeader>

            <Alert>
              <AlertTitle>You are not a member of it</AlertTitle>
              <AlertDescription>
                <p>
                  Creating a workspace does not join it, so it will not appear in your workspace
                  switcher. You can administer it from this panel either way. To work inside it,
                  invite yourself and open the link in the invitation email.
                </p>
              </AlertDescription>
            </Alert>

            {invited ? (
              <Alert variant="success">
                <CircleCheckIcon aria-hidden="true" />
                <AlertTitle>Invitation sent</AlertTitle>
                <AlertDescription>
                  <p>
                    Open the link sent to {email} to join {created.name}.
                  </p>
                </AlertDescription>
              </Alert>
            ) : null}

            <DialogFooter>
              <Button variant="outline" onClick={close}>
                Done
              </Button>
              {email && !invited ? (
                <Button
                  disabled={inviteSelf.isPending}
                  onClick={() => {
                    inviteSelf
                      .mutateAsync({ workspaceId: created.id, email })
                      .then(() => setInvited(true))
                      .catch((error: unknown) => toast.error(toUserMessage(error)))
                  }}
                >
                  {inviteSelf.isPending ? 'Inviting…' : 'Invite yourself'}
                </Button>
              ) : null}
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Create a workspace</DialogTitle>
              <DialogDescription>
                Its roles are seeded automatically. The address cannot be changed afterwards.
              </DialogDescription>
            </DialogHeader>

            <form onSubmit={onSubmit} className="space-y-4" noValidate>
              <FormError error={create.error} title="Could not create the workspace" />

              <Field
                label="Name"
                placeholder="Platform Team"
                error={errors.name}
                {...register('name')}
              />

              <Field
                label="Address"
                placeholder="platform-team"
                hint="Lowercase words separated by single hyphens. It appears in every link to this workspace."
                error={errors.slug}
                {...register('slug')}
              />

              <DialogFooter>
                <Button type="button" variant="outline" disabled={isSubmitting} onClick={close}>
                  Cancel
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Creating…' : 'Create workspace'}
                </Button>
              </DialogFooter>
            </form>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
