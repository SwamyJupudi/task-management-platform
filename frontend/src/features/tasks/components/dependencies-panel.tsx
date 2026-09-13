import { PlusIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { paths } from '@/app/routes/paths'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toUserMessage } from '@/lib/api'

import { useDependencyMutations, useSameProjectTasks } from '../hooks'
import type { Task, TaskLink } from '../types'
import { TaskStatusBadge } from './task-badges'

/**
 * What this task waits on, and what waits on it.
 *
 * Both directions are read off the task itself rather than from
 * `GET /tasks/{id}/dependencies`. The dedicated endpoint exists, but the copy
 * on the task carries the full `PLAT-12` key while that one returns the number
 * alone — so reading the task and invalidating it after a write shows more
 * rather than less, for one fewer request.
 *
 * An edge belongs to the task that is waiting. Removing something from "waiting
 * on" calls this task's address; removing something from "blocking" calls the
 * other task's, because that is where the edge lives. The second needs
 * `task:update` on that other task, which the caller may not have even when
 * they have it here — so the refusal is surfaced as it comes rather than
 * guessed at.
 *
 * Nothing here tries to predict a refusal. The task itself, a duplicate, a task
 * in another project and anything that would close a cycle are all refused
 * server-side with a sentence; a cycle in particular cannot be checked from one
 * task's view of the graph.
 */

function DependencyRow({
  link,
  workspaceSlug,
  onRemove,
  canRemove,
  busy,
}: {
  link: TaskLink
  workspaceSlug: string
  onRemove: () => void
  canRemove: boolean
  busy: boolean
}) {
  return (
    <li className="flex items-center gap-2 py-1.5 text-sm">
      <Link
        to={paths.workspace.task(workspaceSlug, link.id)}
        className="shrink-0 font-mono text-xs text-muted-foreground hover:underline"
      >
        {link.key}
      </Link>
      <span className="min-w-0 flex-1 truncate">{link.title}</span>
      <TaskStatusBadge status={link.status} className="shrink-0" />
      {canRemove ? (
        <Button
          variant="ghost"
          size="icon-sm"
          disabled={busy}
          aria-label={`Remove the link to ${link.key}`}
          onClick={onRemove}
        >
          <XIcon aria-hidden="true" />
        </Button>
      ) : null}
    </li>
  )
}

export function DependenciesPanel({
  task,
  workspaceSlug,
  canEdit,
}: {
  task: Task
  workspaceSlug: string
  /** `task:update` on this task. */
  canEdit: boolean
}) {
  const { add, remove } = useDependencyMutations(task.id)
  const candidates = useSameProjectTasks(canEdit ? task.projectId : undefined)
  const [selected, setSelected] = useState('')

  const busy = add.isPending || remove.isPending

  // The picker offers the rest of the project. The task itself and anything
  // already linked in either direction are dropped, since each is a refusal the
  // backend would answer with and there is no reason to offer it.
  const linked = new Set([
    task.id,
    ...task.blockedBy.map((link) => link.id),
    ...task.blocking.map((link) => link.id),
  ])
  const options = (candidates.data?.content ?? []).filter((candidate) => !linked.has(candidate.id))

  const onRemove = async (holderTaskId: string, dependsOnTaskId: string) => {
    try {
      await remove.mutateAsync({ holderTaskId, dependsOnTaskId })
      toast.success('Link removed.')
    } catch (error) {
      toast.error(toUserMessage(error))
    }
  }

  const nothingLinked = task.blockedBy.length === 0 && task.blocking.length === 0

  return (
    <div className="space-y-4">
      {canEdit ? (
        <div className="flex flex-col gap-2 sm:flex-row">
          <Select
            value={selected}
            onValueChange={setSelected}
            disabled={busy || options.length === 0}
          >
            <SelectTrigger className="h-9 flex-1" aria-label="Choose a task this one waits on">
              <SelectValue
                placeholder={
                  candidates.isPending
                    ? 'Loading the project…'
                    : options.length === 0
                      ? 'Nothing else in this project'
                      : 'This task waits on…'
                }
              />
            </SelectTrigger>
            <SelectContent>
              {options.map((candidate) => (
                <SelectItem key={candidate.id} value={candidate.id}>
                  <span className="font-mono text-xs">{candidate.key}</span> {candidate.title}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button
            disabled={selected === '' || busy}
            onClick={async () => {
              try {
                await add.mutateAsync(selected)
                setSelected('')
                toast.success('Dependency added.')
              } catch (error) {
                // Covers the cycle and the duplicate, both of which the backend
                // explains better than a guess here could.
                toast.error(toUserMessage(error))
              }
            }}
          >
            <PlusIcon aria-hidden="true" />
            Add
          </Button>
        </div>
      ) : null}

      {nothingLinked ? (
        <p className="py-2 text-sm text-muted-foreground">
          {canEdit
            ? 'Nothing linked yet. Record what this task is waiting for.'
            : 'This task is not linked to any other.'}
        </p>
      ) : (
        <div className="space-y-4">
          {task.blockedBy.length > 0 ? (
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Waiting on</p>
              <ul className="divide-y divide-border">
                {task.blockedBy.map((link) => (
                  <DependencyRow
                    key={link.id}
                    link={link}
                    workspaceSlug={workspaceSlug}
                    canRemove={canEdit}
                    busy={busy}
                    // This task holds the edge, so it is this task's address.
                    onRemove={() => void onRemove(task.id, link.id)}
                  />
                ))}
              </ul>
            </div>
          ) : null}

          {task.blocking.length > 0 ? (
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Blocking</p>
              <ul className="divide-y divide-border">
                {task.blocking.map((link) => (
                  <DependencyRow
                    key={link.id}
                    link={link}
                    workspaceSlug={workspaceSlug}
                    canRemove={canEdit}
                    busy={busy}
                    // The other task holds this edge, so removing it is a call
                    // to that task's address rather than to this one's.
                    onRemove={() => void onRemove(link.id, task.id)}
                  />
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}
