import { toast } from 'sonner'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toUserMessage } from '@/lib/api'

import { useAssignTask, useProjectMemberOptions } from '../hooks'
import type { Task } from '../types'

/**
 * Who holds the task.
 *
 * The options are the members of the task's project rather than the workspace
 * roster, because the backend refuses an assignee who is not on the project —
 * offering everybody would mean most choices failing with a sentence about a
 * constraint the person never saw.
 *
 * Clearing is a delete on the same address rather than assigning null, which is
 * why "Unassigned" is a real option here and not an empty value.
 */

/** A Select item cannot hold an empty string, so "nobody" needs a token. */
const UNASSIGNED = '__unassigned__'

function initials(first: string, last: string): string {
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase() || '?'
}

export function AssigneePicker({ task, disabled = false }: { task: Task; disabled?: boolean }) {
  const members = useProjectMemberOptions(task.projectId)
  const assign = useAssignTask(task.id)

  if (!members.data) {
    return (
      <p className="text-sm text-muted-foreground">
        {task.assigneeName ?? 'Unassigned'}
        {members.isError ? ' · the project roster could not be loaded' : ''}
      </p>
    )
  }

  return (
    <Select
      value={task.assigneeUserId ?? UNASSIGNED}
      disabled={disabled || assign.isPending}
      onValueChange={async (value) => {
        try {
          await assign.mutateAsync(value === UNASSIGNED ? null : value)
          toast.success(value === UNASSIGNED ? 'Task unassigned.' : 'Task assigned.')
        } catch (error) {
          toast.error(toUserMessage(error))
        }
      }}
    >
      <SelectTrigger className="h-9 w-full" aria-label={`Assignee for ${task.key}`}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={UNASSIGNED}>Unassigned</SelectItem>
        {members.data.map((member) => (
          <SelectItem key={member.userId} value={member.userId}>
            <span className="flex items-center gap-2">
              <Avatar size="sm">
                <AvatarFallback>{initials(member.firstName, member.lastName)}</AvatarFallback>
              </Avatar>
              {member.firstName} {member.lastName}
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
