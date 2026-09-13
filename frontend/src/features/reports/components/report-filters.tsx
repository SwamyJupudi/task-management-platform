import { XIcon } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useWorkspaceMembers } from '@/features/people'

import { DEFAULT_PERIOD_DAYS, MAX_PERIOD_DAYS } from '../constants'
import { useProjectOptions, useTeamOptions } from '../hooks'
import { withParam } from '../url-state'

/**
 * The filters the report endpoints actually accept, and nothing else.
 *
 * One component with four switches rather than four filter bars, because the
 * five reports draw from the same small set — a project, a team, a person, a
 * window — and each endpoint takes a different subset of it. A control is shown
 * only where the endpoint declares the parameter: the workload report has no
 * person filter because `GET /reports/workload` does not take one, and offering
 * a dropdown that quietly did nothing would be worse than not having it.
 *
 * Nothing is filtered in the browser. Every control writes to the query string
 * and the query string becomes request parameters, because these are aggregates
 * computed server-side: narrowing a chart on this side would redraw the page in
 * hand while claiming to have recomputed the figure.
 *
 * A dropdown whose lookup the caller may not read is hidden rather than shown
 * empty. `project:read`, `team:read` and `member:read` each gate one of them,
 * and none is needed to read a report, so losing one costs a filter rather than
 * the screen.
 */

/** The sentinel for "no filter": a Select item cannot hold an empty value. */
const ANY = '__any__'

/**
 * The parameters this bar owns.
 *
 * Only these count as an active filter and only these are cleared. A report's
 * own sort is its default rather than a filter, and dropping it would move the
 * rows under somebody who asked only to stop narrowing by project.
 */
const FILTER_KEYS = ['projectId', 'teamId', 'assigneeUserId', 'ownerUserId', 'from', 'to'] as const

export function ReportFilters({
  params,
  onChange,
  project = false,
  team = false,
  assignee = false,
  owner = false,
  period = false,
  assigneeLabel = 'Assignee',
  children,
}: {
  params: URLSearchParams
  onChange: (next: URLSearchParams) => void
  project?: boolean
  team?: boolean
  assignee?: boolean
  /** The project report's `ownerUserId`, which is a different parameter. */
  owner?: boolean
  period?: boolean
  /** Overridden where the parameter means something narrower than "assignee". */
  assigneeLabel?: string
  /** The report's own controls — a sort, a granularity — on the same row. */
  children?: ReactNode
}) {
  const projects = useProjectOptions()
  const teams = useTeamOptions()
  const members = useWorkspaceMembers()

  const set = (key: string, value: string | undefined) => onChange(withParam(params, key, value))

  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''

  const active = FILTER_KEYS.some((key) => params.has(key))

  const clear = () => {
    const next = new URLSearchParams(params)
    for (const key of [...FILTER_KEYS, 'page']) next.delete(key)
    onChange(next)
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        {project && projects.data ? (
          <Select
            value={params.get('projectId') ?? ANY}
            onValueChange={(value) => set('projectId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[12rem]" aria-label="Filter by project">
              <SelectValue placeholder="Project" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Every project</SelectItem>
              {projects.data.map((option) => (
                <SelectItem key={option.id} value={option.id}>
                  {option.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        {team && teams.data ? (
          <Select
            value={params.get('teamId') ?? ANY}
            onValueChange={(value) => set('teamId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[11rem]" aria-label="Filter by team">
              <SelectValue placeholder="Team" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Every team</SelectItem>
              {teams.data.map((option) => (
                <SelectItem key={option.id} value={option.id}>
                  {option.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        {assignee && members.data ? (
          <Select
            value={params.get('assigneeUserId') ?? ANY}
            onValueChange={(value) => set('assigneeUserId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[13rem]" aria-label={`Filter by ${assigneeLabel}`}>
              <SelectValue placeholder={assigneeLabel} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Everybody</SelectItem>
              {members.data.map((member) => (
                <SelectItem key={member.userId} value={member.userId}>
                  {member.firstName} {member.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        {owner && members.data ? (
          <Select
            value={params.get('ownerUserId') ?? ANY}
            onValueChange={(value) => set('ownerUserId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[13rem]" aria-label="Filter by project owner">
              <SelectValue placeholder="Owner" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any owner</SelectItem>
              {members.data.map((member) => (
                <SelectItem key={member.userId} value={member.userId}>
                  {member.firstName} {member.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        {period ? (
          <div className="flex items-center gap-2">
            <Label htmlFor="report-from" className="text-xs text-muted-foreground">
              From
            </Label>
            <Input
              id="report-from"
              type="date"
              value={from}
              max={to === '' ? undefined : to}
              onChange={(event) => set('from', event.target.value)}
              className="h-8 w-[9.5rem]"
            />
            <Label htmlFor="report-to" className="text-xs text-muted-foreground">
              To
            </Label>
            <Input
              id="report-to"
              type="date"
              value={to}
              min={from === '' ? undefined : from}
              onChange={(event) => set('to', event.target.value)}
              className="h-8 w-[9.5rem]"
            />
          </div>
        ) : null}

        {children}

        {active ? (
          <Button variant="ghost" size="sm" onClick={clear}>
            <XIcon aria-hidden="true" />
            Clear
          </Button>
        ) : null}
      </div>

      {period ? (
        // Said rather than enforced. The window is resolved in the workspace's
        // own timezone, so computing a default date here would name a different
        // day from the one the figures were counted over.
        <p className="text-xs text-muted-foreground">
          Leave the dates empty for the last {DEFAULT_PERIOD_DAYS} days. A report may cover at most{' '}
          {MAX_PERIOD_DAYS} days.
        </p>
      ) : null}
    </div>
  )
}
