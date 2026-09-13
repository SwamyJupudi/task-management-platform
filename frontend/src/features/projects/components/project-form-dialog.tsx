import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm, type Control, type FieldValues, type Path } from 'react-hook-form'
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
import { toUserMessage } from '@/lib/api'
import { applyFieldErrors } from '@/lib/form'

import { PRIORITY_LABELS, PROJECT_PRIORITIES } from '../constants'
import { useCreateProject, useMemberOptions, useTeamOptions, useUpdateProject } from '../hooks'
import {
  createProjectSchema,
  editProjectSchema,
  parseLabels,
  type CreateProjectValues,
  type EditProjectValues,
} from '../schemas'
import type { Project } from '../types'

/**
 * One dialog for creating and for editing, because the two forms are the same
 * form give or take two fields.
 *
 * The key appears only on create: it is immutable and appears in links, which
 * is why `UpdateProjectRequest` has no field for it. Status and owner are on
 * neither form, for the reason the backend gives for keeping them off the edit
 * as well — a transition is checked against the state machine, and naming an
 * owner also adjusts the roster. Both are their own controls on the detail
 * screen.
 *
 * The dropdowns are bound with `Controller` rather than read with `watch`. A
 * Radix select is not an input React Hook Form can register directly, and
 * `Controller` is the API for exactly that; it also keeps each dropdown
 * subscribed to its own field rather than re-rendering the whole form on every
 * keystroke elsewhere in it.
 *
 * A rejected field comes back on the field that caused it through
 * `applyFieldErrors`, so the backend's own wording reaches the user. Anything
 * it names that the form does not have falls through to the summary at the top.
 */

/** A Select item cannot hold an empty string, so "nothing chosen" needs a token. */
const NONE = '__none__'

export function ProjectFormDialog({
  open,
  onOpenChange,
  project,
  onCreated,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Absent creates; present edits. */
  project?: Project
  onCreated?: (created: Project) => void
}) {
  const editing = project !== undefined

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit project' : 'New project'}</DialogTitle>
          <DialogDescription>
            {editing
              ? 'Change the details of this project. Its key cannot be changed.'
              : 'A new project starts in Planning. You can move it on once it exists.'}
          </DialogDescription>
        </DialogHeader>

        {/* Keyed so a cancelled edit leaves no stale values behind the next
            time the dialog opens. */}
        {editing ? (
          <EditForm key={project.id} project={project} onDone={() => onOpenChange(false)} />
        ) : (
          <CreateForm
            onDone={(created) => {
              onOpenChange(false)
              onCreated?.(created)
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}

function PrioritySelect<T extends FieldValues>({
  control,
  name,
}: {
  control: Control<T>
  name: Path<T>
}) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <div className="space-y-1.5">
          <Label htmlFor="project-priority">Priority</Label>
          <Select value={field.value as string} onValueChange={field.onChange}>
            <SelectTrigger id="project-priority" className="h-9 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PROJECT_PRIORITIES.map((priority) => (
                <SelectItem key={priority} value={priority}>
                  {PRIORITY_LABELS[priority]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}
    />
  )
}

/**
 * The team picker, or an explanation of why there is not one.
 *
 * Listing teams needs `team:read`, which working with projects does not. A
 * caller without it keeps every other control and is told what the gap is,
 * rather than being shown an empty dropdown.
 */
function TeamSelect<T extends FieldValues>({
  control,
  name,
}: {
  control: Control<T>
  name: Path<T>
}) {
  const teams = useTeamOptions()

  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <div className="space-y-1.5">
          <Label htmlFor="project-team">Team</Label>
          {teams.data ? (
            <Select
              value={field.value === '' ? NONE : (field.value as string)}
              onValueChange={(next) => field.onChange(next === NONE ? '' : next)}
            >
              <SelectTrigger id="project-team" className="h-9 w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>No team</SelectItem>
                {teams.data.map((team) => (
                  <SelectItem key={team.id} value={team.id}>
                    {team.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <p className="pt-1 text-xs text-muted-foreground">
              Choosing a team needs the team:read permission.
            </p>
          )}
        </div>
      )}
    />
  )
}

/** The owner picker, offered only on create; afterwards it is its own endpoint. */
function OwnerSelect({ control }: { control: Control<CreateProjectValues> }) {
  const members = useMemberOptions()

  return (
    <Controller
      control={control}
      name="ownerUserId"
      render={({ field }) => (
        <div className="space-y-1.5">
          <Label htmlFor="project-owner">Owner</Label>
          {members.data ? (
            <Select
              value={field.value === '' ? NONE : field.value}
              onValueChange={(next) => field.onChange(next === NONE ? '' : next)}
            >
              <SelectTrigger id="project-owner" className="h-9 w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>No owner</SelectItem>
                {members.data.map((member) => (
                  <SelectItem key={member.userId} value={member.userId}>
                    {member.firstName} {member.lastName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <p className="pt-1 text-xs text-muted-foreground">
              Naming an owner needs the member:read permission. One can be set later.
            </p>
          )}
        </div>
      )}
    />
  )
}

function DescriptionField({
  register,
  message,
}: {
  register: object
  message?: string | undefined
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor="project-description">Description</Label>
      <Textarea id="project-description" rows={3} {...register} />
      {message ? (
        <p role="alert" className="text-xs text-destructive">
          {message}
        </p>
      ) : null}
    </div>
  )
}

function CreateForm({ onDone }: { onDone: (created: Project) => void }) {
  const create = useCreateProject()

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateProjectValues>({
    resolver: zodResolver(createProjectSchema),
    defaultValues: {
      key: '',
      name: '',
      description: '',
      priority: 'MEDIUM',
      teamId: '',
      ownerUserId: '',
      startDate: '',
      endDate: '',
      labels: '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      const created = await create.mutateAsync({
        key: values.key,
        name: values.name,
        ...(values.description !== '' ? { description: values.description } : {}),
        ...(values.ownerUserId !== '' ? { ownerUserId: values.ownerUserId } : {}),
        ...(values.teamId !== '' ? { teamId: values.teamId } : {}),
        priority: values.priority,
        ...(values.startDate !== '' ? { startDate: values.startDate } : {}),
        ...(values.endDate !== '' ? { endDate: values.endDate } : {}),
        labels: parseLabels(values.labels),
      })
      toast.success(`${created.name} created.`)
      onDone(created)
    } catch (error) {
      applyFieldErrors(error, setError, ['key', 'name', 'description', 'startDate', 'endDate'])
    }
  })

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {create.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{toUserMessage(create.error)}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-[9rem_1fr]">
        <Field
          label="Key"
          error={errors.key}
          hint="2 to 10 characters"
          placeholder="PLAT"
          {...register('key')}
        />
        <Field label="Name" error={errors.name} {...register('name')} />
      </div>

      <DescriptionField register={register('description')} message={errors.description?.message} />

      <OwnerSelect control={control} />

      <div className="grid gap-3 sm:grid-cols-2">
        <PrioritySelect control={control} name="priority" />
        <TeamSelect control={control} name="teamId" />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Start date" type="date" error={errors.startDate} {...register('startDate')} />
        <Field label="End date" type="date" error={errors.endDate} {...register('endDate')} />
      </div>

      <Field
        label="Labels"
        hint="Separate with commas."
        error={errors.labels}
        {...register('labels')}
      />

      <DialogFooter>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating…' : 'Create project'}
        </Button>
      </DialogFooter>
    </form>
  )
}

function EditForm({ project, onDone }: { project: Project; onDone: () => void }) {
  const update = useUpdateProject(project.id)

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<EditProjectValues>({
    resolver: zodResolver(editProjectSchema),
    defaultValues: {
      name: project.name,
      description: project.description ?? '',
      priority: project.priority,
      teamId: project.teamId ?? '',
      startDate: project.startDate ?? '',
      endDate: project.endDate ?? '',
      labels: project.labels.join(', '),
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      await update.mutateAsync({
        name: values.name,
        description: values.description,
        priority: values.priority,
        // `clearTeam` rather than a null teamId: the backend leaves an omitted
        // field alone, so detaching has to be said explicitly.
        ...(values.teamId === '' ? { clearTeam: true } : { teamId: values.teamId }),
        // An emptied date is omitted rather than sent as null, because the
        // backend reads a null date as "leave it alone" and there is no
        // clear-date flag the way there is for the team. Sending null would
        // report success and change nothing; omitting it at least makes the
        // request say what actually happens. The hint on the fields tells the
        // user, since the interface cannot offer what the API does not.
        ...(values.startDate === '' ? {} : { startDate: values.startDate }),
        ...(values.endDate === '' ? {} : { endDate: values.endDate }),
        labels: parseLabels(values.labels),
      })
      toast.success('Project updated.')
      onDone()
    } catch (error) {
      applyFieldErrors(error, setError, ['name', 'description', 'startDate', 'endDate'])
    }
  })

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {update.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{toUserMessage(update.error)}</AlertDescription>
        </Alert>
      ) : null}

      <Field label="Name" error={errors.name} {...register('name')} />

      <DescriptionField register={register('description')} message={errors.description?.message} />

      <div className="grid gap-3 sm:grid-cols-2">
        <PrioritySelect control={control} name="priority" />
        <TeamSelect control={control} name="teamId" />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field
          label="Start date"
          type="date"
          hint="A date cannot be removed once set."
          error={errors.startDate}
          {...register('startDate')}
        />
        <Field label="End date" type="date" error={errors.endDate} {...register('endDate')} />
      </div>

      <Field
        label="Labels"
        hint="Separate with commas."
        error={errors.labels}
        {...register('labels')}
      />

      <DialogFooter>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : 'Save changes'}
        </Button>
      </DialogFooter>
    </form>
  )
}
