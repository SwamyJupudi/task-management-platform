import { SearchIcon, XIcon } from 'lucide-react'
import { useEffect, useState } from 'react'

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

import {
  PRIORITY_LABELS,
  SORT_OPTIONS,
  STATUS_LABELS,
  TASK_PRIORITIES,
  TASK_STATUSES,
} from '../constants'
import { useProjectOptions } from '../hooks'
import type { TaskFilters } from '../types'

/**
 * Search, the filters the endpoint accepts, and the sort.
 *
 * Nothing is filtered in the browser. Every control becomes a query parameter,
 * because the list is paged server-side and a client-side filter would narrow
 * the page in hand while claiming to narrow the whole set.
 *
 * `q` matches the title, or an exact `PROJ-12` key — the backend parses a key
 * out of the text and looks it up directly, so one box covers both "find the
 * task about rotation" and "open PLAT-12".
 *
 * The assignee filter is deliberately absent. My Tasks is its own screen and
 * covers the case anybody actually asks for; a picker over every person is
 * worth building when the people feature exists to populate it properly.
 */

/** The sentinel a Select uses for "no filter", since an item cannot hold "". */
const ANY = '__any__'

export function TaskFiltersBar({
  filters,
  sort,
  onFiltersChange,
  onSortChange,
}: {
  filters: TaskFilters
  sort: string
  onFiltersChange: (next: TaskFilters) => void
  onSortChange: (next: string) => void
}) {
  const projects = useProjectOptions()

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

  // Pushed upward on a pause, matching the projects list so the two screens
  // behave the same way under the same gesture.
  useEffect(() => {
    const current = filters.q ?? ''
    if (search === current) return
    const timer = setTimeout(() => {
      onFiltersChange({ ...filters, q: search === '' ? undefined : search })
    }, 300)
    return () => clearTimeout(timer)
  }, [search, filters, onFiltersChange])

  const set = <K extends keyof TaskFilters>(key: K, value: TaskFilters[K]) => {
    onFiltersChange({ ...filters, [key]: value })
  }

  const active =
    filters.status !== undefined ||
    filters.priority !== undefined ||
    filters.projectId !== undefined ||
    filters.label !== undefined ||
    filters.overdue === true ||
    filters.unassigned === true ||
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
            placeholder="Search by title, or type a key like PLAT-12"
            aria-label="Search tasks by title or key"
            className="h-9 pl-8"
          />
        </div>

        <div className="flex items-center gap-2">
          <Label htmlFor="task-sort" className="shrink-0 text-xs text-muted-foreground">
            Sort
          </Label>
          <Select value={sort} onValueChange={onSortChange}>
            <SelectTrigger id="task-sort" className="h-9 w-[11rem]">
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
            set('status', value === ANY ? undefined : (value as TaskFilters['status']))
          }
        >
          <SelectTrigger className="h-8 w-[9rem]" aria-label="Filter by status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY}>Any status</SelectItem>
            {TASK_STATUSES.map((status) => (
              <SelectItem key={status} value={status}>
                {STATUS_LABELS[status]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.priority ?? ANY}
          onValueChange={(value) =>
            set('priority', value === ANY ? undefined : (value as TaskFilters['priority']))
          }
        >
          <SelectTrigger className="h-8 w-[9rem]" aria-label="Filter by priority">
            <SelectValue placeholder="Priority" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY}>Any priority</SelectItem>
            {TASK_PRIORITIES.map((priority) => (
              <SelectItem key={priority} value={priority}>
                {PRIORITY_LABELS[priority]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {projects.data ? (
          <Select
            value={filters.projectId ?? ANY}
            onValueChange={(value) => set('projectId', value === ANY ? undefined : value)}
          >
            <SelectTrigger className="h-8 w-[11rem]" aria-label="Filter by project">
              <SelectValue placeholder="Project" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any project</SelectItem>
              {projects.data.map((project) => (
                <SelectItem key={project.id} value={project.id}>
                  {project.name}
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

        {/* Two booleans the endpoint already understands, as toggles rather
            than another dropdown: both are questions with one useful answer. */}
        <Button
          variant={filters.overdue === true ? 'secondary' : 'outline'}
          size="sm"
          aria-pressed={filters.overdue === true}
          onClick={() => set('overdue', filters.overdue === true ? undefined : true)}
        >
          Overdue
        </Button>

        <Button
          variant={filters.unassigned === true ? 'secondary' : 'outline'}
          size="sm"
          aria-pressed={filters.unassigned === true}
          onClick={() => set('unassigned', filters.unassigned === true ? undefined : true)}
        >
          Unassigned
        </Button>

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
