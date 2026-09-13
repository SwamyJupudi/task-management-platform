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
import type { Project, ProjectStatus } from '../types'

/**
 * Moves a project to another lifecycle state.
 *
 * Only the moves the state machine permits are offered. The backend refuses the
 * rest with a 409 and stays the authority on it; listing a move that is going
 * to be rejected would just be a worse way to find that out.
 *
 * Archiving is one of these moves rather than a separate action, because that
 * is what it is: `ARCHIVED` is a status every other state can reach and which
 * can be left again for any state. There is no archive endpoint to call, and
 * inventing a control that implied otherwise would misdescribe the model.
 */
export function StatusMenu({
  project,
  onSelect,
  disabled = false,
  size = 'sm',
}: {
  project: Project
  onSelect: (status: ProjectStatus) => void
  disabled?: boolean
  size?: 'sm' | 'xs'
}) {
  const targets = ALLOWED_TRANSITIONS[project.status]

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          size={size}
          disabled={disabled || targets.length === 0}
          aria-label={`Change status of ${project.name}. Currently ${STATUS_LABELS[project.status]}.`}
        >
          {STATUS_LABELS[project.status]}
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
