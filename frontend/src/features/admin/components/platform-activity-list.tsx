import { Badge } from '@/components/ui/badge'
import { relativeTime } from '@/lib/datetime'
import type { ActivityEntry } from '@/types/activity'

import { humanise } from '../constants'

/**
 * The platform audit trail.
 *
 * `summary` is composed by the backend when the row is read, from the
 * identifiers in it and the names those resolve to now, so it is rendered as it
 * arrives. A sentence built here would have to re-resolve names the row does
 * not carry, and a sentence stored anywhere would still say somebody's old name
 * a year after they changed it.
 *
 * Every row here happened outside a workspace: account administration and
 * platform-role grants. The trail is disjoint from each workspace's own by
 * construction — a row belongs to exactly one of the two, decided by whether it
 * names a workspace — so nothing on this screen discloses what happened inside
 * a tenant.
 *
 * The request id is shown because it is the key that ties a row to the server
 * logs, which is the next place somebody investigating an incident goes.
 */
export function PlatformActivityList({ entries }: { entries: ActivityEntry[] }) {
  return (
    <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
      {entries.map((entry) => (
        <li key={entry.id} className="space-y-1 px-3 py-3">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <Badge variant="outline" className="shrink-0 font-mono text-xs">
              {entry.action}
            </Badge>
            <span className="text-xs text-muted-foreground">{humanise(entry.entityType)}</span>
            <time
              dateTime={entry.createdAt}
              title={new Date(entry.createdAt).toLocaleString()}
              className="text-xs text-muted-foreground"
            >
              {relativeTime(entry.createdAt)}
            </time>
          </div>

          <p className="text-sm">{entry.summary}</p>

          <p className="text-xs text-muted-foreground">
            {/* Null for an action the platform took itself, and for one whose
                actor has since been removed. Both are ordinary states. */}
            {entry.actorName ?? entry.actorEmail ?? 'The platform'}
            {entry.requestId ? (
              <>
                {' · '}
                <span className="font-mono">{entry.requestId}</span>
              </>
            ) : null}
          </p>
        </li>
      ))}
    </ul>
  )
}
