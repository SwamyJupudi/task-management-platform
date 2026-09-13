import { SearchIcon, XIcon } from 'lucide-react'
import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'

import {
  PRIORITY_LABELS,
  PROJECT_PRIORITIES,
  PROJECT_STATUSES,
  SORT_OPTIONS,
  STATUS_LABELS,
} from '../constants'
import { useMemberOptions, useTeamOptions } from '../hooks'
import type { ProjectFilters } from '../types'

/**
 * Search, the four filters the endpoint accepts, and the sort.
 *
 * Nothing is filtered in the browser. Every control here becomes a query
 * parameter, because the list is paged server-side and a client-side filter
 * would only narrow the page in hand while claiming to narrow the whole set.
 *
 * `q` matches the name or the key, case-insensitively, which is the backend's
 * rule rather than one invented here.
 *
 * The label filter is a plain text box. No endpoint lists the workspace's label
 * catalog, so there is nothing to populate a picker from; the badges on each
 * project are clickable instead, which is how somebody discovers a label worth
 * filtering by.
 *
 * The team and owner pickers disappear for a caller without `team:read` or
 * `member:read`. Losing a filter is the right failure: the projects themselves
 * are readable with `project:read` alone.
 */

/** The sentinel a Select uses for "no filter", since a Select item cannot hold "". */
const ANY = '__any__'

export function ProjectFiltersBar({
  filters,
  sort,
  onFiltersChange,
  onSortChange,
}: {
  filters: ProjectFilters
  sort: string
  onFiltersChange: (next: ProjectFilters) => void
  onSortChange: (next: string) => void
}) {
  const teams = useTeamOptions()
  const members = useMemberOptions()

  // The search box is typed into, so it is local state that pushes upward on a
  // pause. Driving it straight from the URL would issue a request per keystroke
  // and move the caret every time the query string was rewritten.
  const [search, setSearch] = useState(filters.q ?? '')

  // Keeps the box in step when `q` changes from somewhere else — the clear
  // button, or a link that arrives with its own search. Adjusted during render
  // against the last value seen rather than in an effect, which would render
  // once with the stale text and then again to correct it.
  const [lastQ, setLastQ] = useState(filters.q ?? '')
  if ((filters.q ?? '') !== lastQ) {
    setLastQ(filters.q ?? '')
    setSearch(filters.q ?? '')
  }

  useEffect(() => {
    const current = filters.q ?? ''
    if (search === current) return
    const timer = setTimeout(() => {
      onFiltersChange({ ...filters, q: search === '' ? undefined : search })
    }, 300)
    return () => clearTimeout(timer)
  }, [search, filters, onFiltersChange])

  const set = <K extends keyof ProjectFilters>(key: K, value: ProjectFilters[K]) => {
    onFiltersChange({ ...filters, [key]: value })
  }

  const active =
    filters.status !== undefined ||
    filters.priority !== undefined ||
    filters.teamId !== undefined ||
    filters.ownerUserId !== undefined ||
    filters.label !== undefined ||
    (filters.q !== undefined && filters.q !== '')

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <SearchIcon
            className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by name or key"
            aria-label="Search projects by name or key"
            className="h-9 pl-8"
          />
        </div>

        <div className="flex items-center gap-2">
          <Label htmlFor="project-sort" className="shrink-0 text-xs text-muted-foreground">
            Sort
          </Label>
          <Select value={sort} onValueChange={onSortChange}>
            <SelectTrigger id="project-sort" className="h-9 w-[11rem]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SORT_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={filters.status ?? ANY}
          onValueChange={(value) =>
            set('status', value === ANY ? undefined : (value as ProjectFilters['status']))
          }
        >
          <SelectTrigger className="h-8 w-[8.5rem]" aria-label="Filter by status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY}>Any status</SelectItem>
            {PROJECT_STATUSES.map((status) => (
              <SelectItem key={status} value={status}>
                {STATUS_LABELS[status]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.priority ?? ANY}
          onValueChange={(value) =>
            set('priority', value === ANY ? undefined : (value as ProjectFilters['priority']))
          }
        >
          <SelectTrigger className="h-8 w-[8.5rem]" aria-label="Filter by priority">
            <SelectValue placeholder="Priority" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY}>Any priority</SelectItem>
            {PROJECT_PRIORITIES.map((priority) => (
              <SelectItem key={priority} value={priority}>
                {PRIORITY_LABELS[priority]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {teams.data ? (
          <Select
            value={filters.teamId ?? ANY}
            onValueChange={(value) => set('teamId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[10rem]" aria-label="Filter by team">
              <SelectValue placeholder="Team" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any team</SelectItem>
              {teams.data.map((team) => (
                <SelectItem key={team.id} value={team.id}>
                  {team.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        {members.data ? (
          <Select
            value={filters.ownerUserId ?? ANY}
            onValueChange={(value) => set('ownerUserId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[11rem]" aria-label="Filter by owner">
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

        <Input
          value={filters.label ?? ''}
          onChange={(event) =>
            set('label', event.target.value === '' ? undefined : event.target.value)
          }
          placeholder="Label"
          aria-label="Filter by label"
          className="h-8 w-[8rem]"
        />

        {active ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSearch('')
              onFiltersChange({})
            }}
          >
            <XIcon aria-hidden="true" />
            Clear
          </Button>
        ) : null}
      </div>
    </div>
  )
}
