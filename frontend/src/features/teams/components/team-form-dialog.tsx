import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { Field } from '@/components/common/field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useWorkspaceMembers } from '@/features/people'
import { toUserMessage } from '@/lib/api'
import { applyFieldErrors } from '@/lib/form'

import { useTeamMutations } from '../hooks'
import { teamSchema, type TeamValues } from '../schemas'
import type { Team } from '../types'

/**
 * One dialog for creating a team and for editing one.
 *
 * The lead appears only on create, where the API accepts one and adds that
 * person to the team as it is made. Afterwards the lead is its own control on
 * the detail screen, because assigning one also changes the roster and
 * `UpdateTeamRequest` deliberately has no field for it.
 *
 * The member picker comes from the people feature rather than a private copy of
 * the same call: that feature owns the workspace roster.
 */

/** A Select item cannot hold an empty string, so "nobody" needs a token. */
const NO_LEAD = '__none__'

export function TeamFormDialog({
  open,
  onOpenChange,
  team,
  onCreated,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Absent creates; present edits. */
  team?: Team
  onCreated?: (created: Team) => void
}) {
  const editing = team !== undefined
  const { create, update } = useTeamMutations()
  const members = useWorkspaceMembers()

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TeamValues>({
    resolver: zodResolver(teamSchema),
    defaultValues: {
      name: team?.name ?? '',
      description: team?.description ?? '',
      leadUserId: '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      if (editing) {
        await update.mutateAsync({
          id: team.id,
          body: { name: values.name, description: values.description },
        })
        toast.success('Team updated.')
        onOpenChange(false)
        return
      }

      const created = await create.mutateAsync({
        name: values.name,
        ...(values.description !== '' ? { description: values.description } : {}),
        ...(values.leadUserId !== '' ? { leadUserId: values.leadUserId } : {}),
      })
      toast.success(`${created.name} created.`)
      onOpenChange(false)
      onCreated?.(created)
    } catch (error) {
      applyFieldErrors(error, setError, ['name', 'description', 'leadUserId'])
    }
  })

  const failure = editing ? update.error : create.error
  const failed = editing ? update.isError : create.isError

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit team' : 'New team'}</DialogTitle>
          <DialogDescription>
            {editing
              ? 'Change the name or the description. The lead and the roster have their own controls.'
              : 'A team groups people and the projects they run.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {failed ? (
            <Alert variant="destructive">
              <AlertDescription>{toUserMessage(failure)}</AlertDescription>
            </Alert>
          ) : null}

          <Field label="Name" error={errors.name} {...register('name')} />

          <div className="space-y-1.5">
            <Label htmlFor="team-description">Description</Label>
            <Textarea id="team-description" rows={3} {...register('description')} />
            {errors.description ? (
              <p role="alert" className="text-xs text-destructive">
                {errors.description.message}
              </p>
            ) : null}
          </div>

          {!editing ? (
            <Controller
              control={control}
              name="leadUserId"
              render={({ field }) => (
                <div className="space-y-1.5">
                  <Label htmlFor="team-lead">Lead</Label>
                  {members.data ? (
                    <Select
                      value={field.value === '' ? NO_LEAD : field.value}
                      onValueChange={(next) => field.onChange(next === NO_LEAD ? '' : next)}
                    >
                      <SelectTrigger id="team-lead" className="h-9 w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NO_LEAD}>No lead</SelectItem>
                        {members.data.map((member) => (
                          <SelectItem key={member.userId} value={member.userId}>
                            {member.firstName} {member.lastName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : (
                    <p className="pt-1 text-xs text-muted-foreground">
                      Naming a lead needs the member:read permission. One can be set later.
                    </p>
                  )}
                </div>
              )}
            />
          ) : null}

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : editing ? 'Save changes' : 'Create team'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
