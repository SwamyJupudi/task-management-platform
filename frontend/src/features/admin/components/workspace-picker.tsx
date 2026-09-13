import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import type { WorkspaceSummary } from '../types'

/** A Select item cannot hold an empty value, so "none" needs a token. */
const NONE = '__none__'

/**
 * Choosing which workspace a screen is looking at.
 *
 * Two screens in the panel are addressed by workspace rather than being
 * installation-wide: the role editor, because roles are seeded per workspace,
 * and the team overview, because no endpoint lists teams across workspaces.
 * The workspace here is the address of the records being read, never a scope
 * that widens what the caller may see — that has already been settled by the
 * platform grant before this control is rendered.
 *
 * An archived workspace is offered rather than hidden. Its roles and teams are
 * still readable, and pretending it does not exist would make an administrator
 * wonder where it went; the screens that write say instead why they cannot.
 */
export function WorkspacePicker({
  workspaces,
  value,
  onChange,
  id,
  label = 'Workspace',
  placeholder = 'Choose a workspace',
  allowNone = false,
  noneLabel = 'Every workspace',
}: {
  workspaces: WorkspaceSummary[]
  value: string | undefined
  onChange: (next: string | undefined) => void
  id: string
  label?: string
  placeholder?: string
  /** Set where "no workspace chosen" is a filter meaning "all of them". */
  allowNone?: boolean
  noneLabel?: string
}) {
  return (
    <div className="flex items-center gap-2">
      <Label htmlFor={id} className="shrink-0 text-xs text-muted-foreground">
        {label}
      </Label>
      <Select
        value={value ?? NONE}
        onValueChange={(next) => onChange(next === NONE ? undefined : next)}
      >
        <SelectTrigger id={id} className="h-8 w-[14rem]">
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {allowNone ? <SelectItem value={NONE}>{noneLabel}</SelectItem> : null}
          {workspaces.map((workspace) => (
            <SelectItem key={workspace.id} value={workspace.id}>
              {workspace.name}
              {workspace.status === 'ARCHIVED' ? ' (archived)' : ''}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
