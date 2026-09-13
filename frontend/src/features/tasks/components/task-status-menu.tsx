import { ChevronRightIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { ALLOWED_TRANSITIONS, STATUS_LABELS } from '../constants'
import type { Task, TaskStatus } from '../types'

/**
 * Moves a task to another status.
 *
 * Only the moves the state machine permits are offered. The backend refuses the
 * rest with a 409 and stays the authority on it; listing a move that is going
 * to be rejected would just be a worse way to find that out.
 *
 * A blocked task is still movable. The requirements state no rule about
 * starting or finishing work that something else is waiting on, so the
 * interface reports the fact and leaves the judgement to the person.
 */
export function TaskStatusMenu({
  task,
  onSelect,
  disabled = false,
  size = 'sm',
}: {
  task: Task
  onSelect: (status: TaskStatus) => void
  disabled?: boolean
  size?: 'sm' | 'xs'
}) {
  const targets = ALLOWED_TRANSITIONS[task.status]

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          size={size}
          disabled={disabled || targets.length === 0}
          aria-label={`Change status of ${task.key}. Currently ${STATUS_LABELS[task.status]}.`}
        >
          {STATUS_LABELS[task.status]}
          <ChevronRightIcon aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-48">
        <DropdownMenuLabel>Move to</DropdownMenuLabel>
        {targets.map((target) => (
          <DropdownMenuItem key={target} onSelect={() => onSelect(target)}>
            {STATUS_LABELS[target]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
