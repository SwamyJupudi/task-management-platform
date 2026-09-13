import { SearchIcon, XIcon } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { ACCOUNT_STATUSES } from '../constants'

/**
 * The three filters `GET /admin/accounts` accepts, and nothing else.
 *
 * Nothing is filtered in the browser: the listing is paged server-side, so a
 * client-side filter would narrow the twenty rows in hand while claiming to
 * narrow the installation.
 *
 * `locked` is a toggle rather than a fourth status, because it is a different
 * column answering a different question. "Who cannot sign in and does not know
 * why" is what an administrator usually arrives with, and the answer is a
 * lockout rather than a status.
 */

/** A Select item cannot hold an empty value. */
const ANY = '__any__'

export function AccountFilters({
  params,
  onChange,
  children,
}: {
  params: URLSearchParams
  onChange: (next: URLSearchParams) => void
  /** The screen's sort, on the same row. */
  children?: ReactNode
}) {
  const currentSearch = params.get('search') ?? ''

  // Typed into, so it is local state pushed upward on a pause. Driving it from
  // the URL would issue a request per keystroke and move the caret each time
  // the query string was rewritten.
  const [search, setSearch] = useState(currentSearch)

  // Keeps the box in step when the value changes from elsewhere — the clear
  // button, or a link arriving with its own search. Adjusted during render
  // against the last value seen rather than in an effect, which would render
  // once with stale text and then again to correct it.
  const [lastSearch, setLastSearch] = useState(currentSearch)
  if (currentSearch !== lastSearch) {
    setLastSearch(currentSearch)
    setSearch(currentSearch)
  }

  useEffect(() => {
    if (search === currentSearch) return
    const timer = setTimeout(() => {
      const next = new URLSearchParams(params)
      if (search === '') next.delete('search')
      else next.set('search', search)
      next.delete('page')
      onChange(next)
    }, 300)
    return () => clearTimeout(timer)
  }, [search, currentSearch, params, onChange])

  const set = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(params)
    if (value === undefined) next.delete(key)
    else next.set(key, value)
    next.delete('page')
    onChange(next)
  }

  const locked = params.get('locked') === 'true'
  const active = currentSearch !== '' || params.has('status') || locked

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
      <div className="relative flex-1 sm:min-w-[16rem]">
        <SearchIcon
          className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search by name or email"
          aria-label="Search accounts by name or email"
          className="h-8 pl-8"
        />
      </div>

      <Select
        value={params.get('status') ?? ANY}
        onValueChange={(value) => set('status', value === ANY ? undefined : value)}
      >
        <SelectTrigger className="h-8 w-[12rem]" aria-label="Filter by account status">
          <SelectValue placeholder="Status" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ANY}>Any status</SelectItem>
          {ACCOUNT_STATUSES.map((status) => (
            <SelectItem key={status.value} value={status.value}>
              {status.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Button
        variant={locked ? 'secondary' : 'outline'}
        size="sm"
        aria-pressed={locked}
        onClick={() => set('locked', locked ? undefined : 'true')}
      >
        Locked out
      </Button>

      {children}

      {active ? (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            setSearch('')
            const next = new URLSearchParams(params)
            for (const key of ['search', 'status', 'locked', 'page']) next.delete(key)
            onChange(next)
          }}
        >
          <XIcon aria-hidden="true" />
          Clear
        </Button>
      ) : null}
    </div>
  )
}
