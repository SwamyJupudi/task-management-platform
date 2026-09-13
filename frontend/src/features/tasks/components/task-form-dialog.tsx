import { zodResolver } from '@hookform/resolvers/zod'
import {
  Controller,
  useForm,
  useWatch,
  type Control,
  type FieldValues,
  type Path,
} from 'react-hook-form'
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

import { PRIORITY_LABELS, TASK_PRIORITIES } from '../constants'
import { useCreateTask, useProjectMemberOptions, useProjectOptions, useUpdateTask } from '../hooks'
import {
  createTaskSchema,
  editTaskSchema,
  parseLabels,
  parseMinutes,
  type CreateTaskValues,
  type EditTaskValues,
} from '../schemas'
import type { Task } from '../types'

/**
 * One dialog for raising a task and for editing one.
 *
 * The project appears only on create: a task is numbered against its project
 * and the API supports no way to move it, so offering the choice on an edit
 * would imply something the platform cannot do.
 *
 * Status and assignee are on neither form. Each has its own endpoint and its
 * own permission — somebody may be allowed to move work along without being
 * allowed to rewrite it — so folding them into a general edit would quietly
 * require more permission than the action needs.
 *
 * The dropdowns are bound with `Controller` rather than read with `watch`: a
 * Radix select is not an input React Hook Form can register directly, and this
 * keeps each dropdown subscribed to its own field rather than re-rendering the
 * whole form on every keystroke elsewhere in it.
 */

/** A Select item cannot hold an empty string, so "nothing chosen" needs a token. */
const NONE = '__none__'

export function TaskFormDialog({
  open,
  onOpenChange,
  task,
  /** Preselects the project when the dialog is opened from inside one. */
  defaultProjectId,
  onCreated,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Absent creates; present edits. */
  task?: Task
  defaultProjectId?: string
  onCreated?: (created: Task) => void
}) {
  const editing = task !== undefined

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit task' : 'New task'}</DialogTitle>
          <DialogDescription>
            {editing
              ? 'Change the details of this task. Its status and assignee have their own controls.'
              : 'A new task starts in To do and is numbered against its project.'}
          </DialogDescription>
        </DialogHeader>

        {/* Keyed so a cancelled edit leaves no stale values behind the next
            time the dialog opens. */}
        {editing ? (
          <EditForm key={task.id} task={task} onDone={() => onOpenChange(false)} />
        ) : (
          <CreateForm
            defaultProjectId={defaultProjectId}
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
          <Label htmlFor="task-priority">Priority</Label>
          <Select value={field.value as string} onValueChange={field.onChange}>
            <SelectTrigger id="task-priority" className="h-9 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TASK_PRIORITIES.map((priority) => (
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

/** The project the task is raised in. Create only, and required. */
function ProjectSelect({
  control,
  message,
}: {
  control: Control<CreateTaskValues>
  message?: string | undefined
}) {
  const projects = useProjectOptions()

  return (
    <Controller
      control={control}
      name="projectId"
      render={({ field }) => (
        <div className="space-y-1.5">
          <Label htmlFor="task-project">Project</Label>
          {projects.data ? (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="task-project" className="h-9 w-full" aria-invalid={!!message}>
                <SelectValue placeholder="Choose a project" />
              </SelectTrigger>
              <SelectContent>
                {projects.data.map((project) => (
                  <SelectItem key={project.id} value={project.id}>
                    {project.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <p className="pt-1 text-xs text-muted-foreground">
              Choosing a project needs the project:read permission.
            </p>
          )}
          {message ? (
            <p role="alert" className="text-xs text-destructive">
              {message}
            </p>
          ) : null}
        </div>
      )}
    />
  )
}

/**
 * The assignee, offered on create because the API accepts one there.
 *
 * The options are the chosen project's members, so the picker is empty until a
 * project is chosen — which is the backend's rule made visible rather than a
 * limitation.
 */
function AssigneeSelect({
  control,
  projectId,
}: {
  control: Control<CreateTaskValues>
  projectId: string
}) {
  const members = useProjectMemberOptions(projectId === '' ? undefined : projectId)

  return (
    <Controller
      control={control}
      name="assigneeUserId"
      render={({ field }) => (
        <div className="space-y-1.5">
          <Label htmlFor="task-assignee">Assignee</Label>
          <Select
            value={field.value === '' ? NONE : field.value}
            onValueChange={(next) => field.onChange(next === NONE ? '' : next)}
            disabled={projectId === '' || !members.data}
          >
            <SelectTrigger id="task-assignee" className="h-9 w-full">
              <SelectValue
                placeholder={projectId === '' ? 'Choose a project first' : 'Unassigned'}
              />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>Unassigned</SelectItem>
              {(members.data ?? []).map((member) => (
                <SelectItem key={member.userId} value={member.userId}>
                  {member.firstName} {member.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
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
      <Label htmlFor="task-description">Description</Label>
      <Textarea id="task-description" rows={4} {...register} />
      {message ? (
        <p role="alert" className="text-xs text-destructive">
          {message}
        </p>
      ) : null}
    </div>
  )
}

function CreateForm({
  defaultProjectId,
  onDone,
}: {
  defaultProjectId?: string
  onDone: (created: Task) => void
}) {
  const create = useCreateTask()

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateTaskValues>({
    resolver: zodResolver(createTaskSchema),
    defaultValues: {
      projectId: defaultProjectId ?? '',
      title: '',
      description: '',
      priority: 'MEDIUM',
      assigneeUserId: '',
      startDate: '',
      dueDate: '',
      estimatedMinutes: '',
      labels: '',
    },
  })

  // Subscribed to on its own rather than read through `watch`, because the
  // assignee options depend on it and nothing else in the form does: this
  // re-renders the picker when the project changes and leaves the rest alone.
  const projectId = useWatch({ control, name: 'projectId' })

  const onSubmit = handleSubmit(async (values) => {
    try {
      const created = await create.mutateAsync({
        projectId: values.projectId,
        body: {
          title: values.title,
          ...(values.description !== '' ? { description: values.description } : {}),
          ...(values.assigneeUserId !== '' ? { assigneeUserId: values.assigneeUserId } : {}),
          priority: values.priority,
          ...(values.startDate !== '' ? { startDate: values.startDate } : {}),
          ...(values.dueDate !== '' ? { dueDate: values.dueDate } : {}),
          ...(parseMinutes(values.estimatedMinutes) !== undefined
            ? { estimatedMinutes: parseMinutes(values.estimatedMinutes) }
            : {}),
          labels: parseLabels(values.labels),
        },
      })
      toast.success(`${created.key} created.`)
      onDone(created)
    } catch (error) {
      applyFieldErrors(error, setError, [
        'title',
        'description',
        'startDate',
        'dueDate',
        'estimatedMinutes',
      ])
    }
  })

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {create.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{toUserMessage(create.error)}</AlertDescription>
        </Alert>
      ) : null}

      <ProjectSelect control={control} message={errors.projectId?.message} />

      <Field label="Title" error={errors.title} {...register('title')} />

      <DescriptionField register={register('description')} message={errors.description?.message} />

      <div className="grid gap-3 sm:grid-cols-2">
        <PrioritySelect control={control} name="priority" />
        <AssigneeSelect control={control} projectId={projectId} />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Start date" type="date" error={errors.startDate} {...register('startDate')} />
        <Field label="Due date" type="date" error={errors.dueDate} {...register('dueDate')} />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field
          label="Estimate"
          hint="In minutes."
          inputMode="numeric"
          error={errors.estimatedMinutes}
          {...register('estimatedMinutes')}
        />
        <Field
          label="Labels"
          hint="Separate with commas."
          error={errors.labels}
          {...register('labels')}
        />
      </div>

      <DialogFooter>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating…' : 'Create task'}
        </Button>
      </DialogFooter>
    </form>
  )
}

function EditForm({ task, onDone }: { task: Task; onDone: () => void }) {
  const update = useUpdateTask(task.id)

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<EditTaskValues>({
    resolver: zodResolver(editTaskSchema),
    defaultValues: {
      title: task.title,
      description: task.description ?? '',
      priority: task.priority,
      startDate: task.startDate ?? '',
      dueDate: task.dueDate ?? '',
      estimatedMinutes: task.estimatedMinutes?.toString() ?? '',
      actualMinutes: task.actualMinutes?.toString() ?? '',
      labels: task.labels.join(', '),
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    const hadDates = task.startDate !== null || task.dueDate !== null
    const wantsNoDates = values.startDate === '' && values.dueDate === ''

    try {
      await update.mutateAsync({
        title: values.title,
        description: values.description,
        priority: values.priority,
        // A null date reads as "leave it alone" server-side, so emptying both
        // boxes is expressed with the flag the API provides for exactly that.
        // Emptying only one is not expressible and is left alone, which the
        // hint on the fields says.
        ...(hadDates && wantsNoDates
          ? { clearDates: true }
          : {
              ...(values.startDate !== '' ? { startDate: values.startDate } : {}),
              ...(values.dueDate !== '' ? { dueDate: values.dueDate } : {}),
            }),
        ...(parseMinutes(values.estimatedMinutes) !== undefined
          ? { estimatedMinutes: parseMinutes(values.estimatedMinutes) }
          : {}),
        ...(parseMinutes(values.actualMinutes) !== undefined
          ? { actualMinutes: parseMinutes(values.actualMinutes) }
          : {}),
        labels: parseLabels(values.labels),
      })
      toast.success('Task updated.')
      onDone()
    } catch (error) {
      applyFieldErrors(error, setError, [
        'title',
        'description',
        'startDate',
        'dueDate',
        'estimatedMinutes',
        'actualMinutes',
      ])
    }
  })

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {update.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{toUserMessage(update.error)}</AlertDescription>
        </Alert>
      ) : null}

      <Field label="Title" error={errors.title} {...register('title')} />

      <DescriptionField register={register('description')} message={errors.description?.message} />

      <PrioritySelect control={control} name="priority" />

      <div className="grid gap-3 sm:grid-cols-2">
        <Field
          label="Start date"
          type="date"
          hint="Clear both dates to remove them."
          error={errors.startDate}
          {...register('startDate')}
        />
        <Field label="Due date" type="date" error={errors.dueDate} {...register('dueDate')} />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field
          label="Estimate"
          hint="In minutes."
          inputMode="numeric"
          error={errors.estimatedMinutes}
          {...register('estimatedMinutes')}
        />
        <Field
          label="Time spent"
          hint="In minutes."
          inputMode="numeric"
          error={errors.actualMinutes}
          {...register('actualMinutes')}
        />
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
