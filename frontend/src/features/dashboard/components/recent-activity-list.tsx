import type { ActivityEntry } from '../types'

/**
 * What the caller themselves did, newest first.
 *
 * Not what happened around them: browsing the workspace's audit trail is an
 * administrator's permission, and this panel is not a way around it. The
 * backend narrows it to the caller before it is sent.
 *
 * `summary` is composed by the backend when the row is read, from the
 * identifiers in it and the names those resolve to now, so it is rendered
 * verbatim. Building the sentence here would mean a second copy of the rule and
 * a line that goes stale the first time somebody is renamed.
 */
function relativeTime(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''

  const seconds = Math.round((Date.now() - then) / 1000)
  if (seconds < 60) return 'just now'

  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`

  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`

  const days = Math.round(hours / 24)
  if (days < 7) return `${days}d ago`

  return new Date(then).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

export function RecentActivityList({ entries }: { entries: ActivityEntry[] }) {
  return (
    <ol className="divide-y divide-border">
      {entries.map((entry) => (
        <li key={entry.id} className="flex items-baseline gap-3 py-2 first:pt-0 last:pb-0">
          <p className="min-w-0 flex-1 text-sm">{entry.summary}</p>
          <time
            dateTime={entry.createdAt}
            title={new Date(entry.createdAt).toLocaleString()}
            className="shrink-0 text-xs whitespace-nowrap text-muted-foreground"
          >
            {relativeTime(entry.createdAt)}
          </time>
        </li>
      ))}
    </ol>
  )
}
