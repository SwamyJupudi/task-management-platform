import { zodResolver } from '@hookform/resolvers/zod'
import { PlusIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { toUserMessage } from '@/lib/api'
import { cn } from '@/lib/utils'

import { useProjectMemberOptions, useSubtaskMutations, useSubtasks } from '../hooks'
import { createSubtaskSchema, type CreateSubtaskValues } from '../schemas'
import type { Subtask, Task } from '../types'

/**
 * The checklist under a task.
 *
 * Two permissions, kept apart because the backend keeps them apart: adding,
 * renaming, assigning and removing an item need `task:update`, while ticking
 * one off needs `task:change_status`. Somebody may therefore be able to work
 * through a checklist without being able to change what is on it, which is a
 * real arrangement rather than a quirk.
 *
 * The tick is a status change to DONE and back to TODO. Moving to the status an
 * item already holds is a conflict server-side, so the checkbox only ever sends
 * the opposite of what is there.
 *
 * Ordering is shown but not editable. `position` is settable through the API,
 * but a drag-to-reorder needs a gapless ordering design the platform has not
 * settled, so the list renders in the order the backend returns.
 */

function AddSubtaskForm({ taskId, disabled }: { taskId: string; disabled: boolean }) {
  const { create } = useSubtaskMutations(taskId)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateSubtaskValues>({
    resolver: zodResolver(createSubtaskSchema),
    defaultValues: { title: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      await create.mutateAsync({ title: values.title })
      // Cleared rather than left filled, because a checklist is usually typed
      // several items at a time and the field keeps focus for the next one.
      reset({ title: '' })
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  })

  return (
    <form onSubmit={onSubmit} className="space-y-1.5" noValidate>
      <div className="flex gap-2">
        <Input
          {...register('title')}
          placeholder="Add a checklist item"
          aria-label="New checklist item"
          aria-invalid={errors.title ? true : undefined}
          className="h-9"
          disabled={disabled}
        />
        <Button type="submit" size="sm" disabled={disabled || isSubmitting}>
          <PlusIcon aria-hidden="true" />
          Add
        </Button>
      </div>
      {errors.title ? (
        <p role="alert" className="text-xs text-destructive">
          {errors.title.message}
        </p>
      ) : null}
    </form>
  )
}

/** A Select item cannot hold an empty string, so "nobody" needs a token. */
const UNASSIGNED = '__unassigned__'

function SubtaskRow({
  subtask,
  task,
  canEdit,
  canTick,
}: {
  subtask: Subtask
  task: Task
  canEdit: boolean
  canTick: boolean
}) {
  const { update, changeStatus, remove } = useSubtaskMutations(task.id)
  const members = useProjectMemberOptions(task.projectId)

  const [title, setTitle] = useState(subtask.title)
  const [editing, setEditing] = useState(false)

  const busy = update.isPending || changeStatus.isPending || remove.isPending

  const saveTitle = async () => {
    setEditing(false)
    const trimmed = title.trim()
    if (trimmed === '' || trimmed === subtask.title) {
      setTitle(subtask.title)
      return
    }
    try {
      await update.mutateAsync({ subtaskId: subtask.id, body: { title: trimmed } })
    } catch (error) {
      setTitle(subtask.title)
      toast.error(toUserMessage(error))
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-2 py-2">
      <Checkbox
        checked={subtask.completed}
        disabled={!canTick || busy}
        aria-label={`Mark ${subtask.title} ${subtask.completed ? 'not done' : 'done'}`}
        onCheckedChange={async () => {
          try {
            // Always the opposite of the current state: asking for the status it
            // already holds is a conflict rather than a no-op.
            await changeStatus.mutateAsync({
              subtaskId: subtask.id,
              status: subtask.completed ? 'TODO' : 'DONE',
            })
          } catch (error) {
            toast.error(toUserMessage(error))
          }
        }}
      />

      {editing && canEdit ? (
        <Input
          value={title}
          autoFocus
          className="h-8 flex-1"
          aria-label={`Rename ${subtask.title}`}
          onChange={(event) => setTitle(event.target.value)}
          onBlur={saveTitle}
          onKeyDown={(event) => {
            if (event.key === 'Enter') void saveTitle()
            if (event.key === 'Escape') {
              setTitle(subtask.title)
              setEditing(false)
            }
          }}
        />
      ) : (
        <button
          type="button"
          disabled={!canEdit}
          onClick={() => setEditing(true)}
          className={cn(
            'min-w-0 flex-1 truncate text-left text-sm',
            subtask.completed && 'text-muted-foreground line-through',
            canEdit && 'hover:underline',
          )}
        >
          {subtask.title}
        </button>
      )}

      {canEdit && members.data ? (
        <Select
          value={subtask.assigneeUserId ?? UNASSIGNED}
          disabled={busy}
          onValueChange={async (value) => {
            try {
              await update.mutateAsync({
                subtaskId: subtask.id,
                // Clearing is a flag, because an omitted assignee means
                // "leave it alone" rather than "nobody".
                body: value === UNASSIGNED ? { clearAssignee: true } : { assigneeUserId: value },
              })
            } catch (error) {
              toast.error(toUserMessage(error))
            }
          }}
        >
          <SelectTrigger className="h-8 w-[9.5rem]" aria-label={`Assignee for ${subtask.title}`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={UNASSIGNED}>Unassigned</SelectItem>
            {members.data.map((member) => (
              <SelectItem key={member.userId} value={member.userId}>
                {member.firstName} {member.lastName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      ) : subtask.assigneeName ? (
        <span className="shrink-0 text-xs text-muted-foreground">{subtask.assigneeName}</span>
      ) : null}

      {canEdit ? (
        <Button
          variant="ghost"
          size="icon-sm"
          disabled={busy}
          aria-label={`Remove ${subtask.title}`}
          onClick={async () => {
            try {
              await remove.mutateAsync(subtask.id)
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

export function SubtaskPanel({
  task,
  canEdit,
  canTick,
}: {
  task: Task
  /** `task:update` on this task: add, rename, assign, remove. */
  canEdit: boolean
  /** `task:change_status` on this task: tick items off. */
  canTick: boolean
}) {
  const subtasks = useSubtasks(task.id)

  if (subtasks.isError) {
    return <ErrorState error={subtasks.error} onRetry={() => void subtasks.refetch()} />
  }

  if (subtasks.isPending) {
    return (
      <div className="space-y-2" role="status" aria-live="polite">
        <span className="sr-only">Loading the checklist</span>
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
      </div>
    )
  }

  const items = subtasks.data
  const done = items.filter((item) => item.completed).length
  const percent = items.length === 0 ? 0 : Math.round((done / items.length) * 100)

  return (
    <div className="space-y-4">
      {items.length > 0 ? (
        <div className="space-y-1.5">
          <div className="flex items-baseline justify-between">
            <span className="text-xs text-muted-foreground">
              {done} of {items.length} done
            </span>
            <span className="text-xs font-medium tabular-nums">{percent}%</span>
          </div>
          <Progress value={percent} aria-label={`Checklist: ${done} of ${items.length} done`} />
        </div>
      ) : null}

      {items.length === 0 ? (
        <EmptyState
          title="No checklist yet"
          description={
            canEdit
              ? 'Break this task into steps that can be ticked off.'
              : 'Steps added to this task will appear here.'
          }
          className="border-0 px-0 py-6"
        />
      ) : (
        <ul className="divide-y divide-border">
          {items.map((subtask) => (
            <SubtaskRow
              key={subtask.id}
              subtask={subtask}
              task={task}
              canEdit={canEdit}
              canTick={canTick}
            />
          ))}
        </ul>
      )}

      {canEdit ? <AddSubtaskForm taskId={task.id} disabled={false} /> : null}
    </div>
  )
}
