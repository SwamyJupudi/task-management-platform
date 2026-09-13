/**
 * How a timestamp reads beside something that just happened.
 *
 * Coarse on purpose: a discussion wants "3h ago", not a date somebody has to
 * subtract in their head. Past a week the absolute date is more useful than a
 * growing count of days, so it switches.
 *
 * The exact moment is never lost — every caller puts it in a `title` and a
 * `dateTime` on the element, so hovering and assistive technology both get the
 * real thing.
 */
export function relativeTime(iso: string): string {
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

/**
 * Renders an effort field the way it is entered: in minutes.
 *
 * Hours would read better but would need a unit the API does not carry, and
 * rounding 90 minutes to "1.5h" and back loses what was typed.
 *
 * Shared rather than owned by the tasks feature: a task shows its own estimate
 * and the workload report sums the estimates of everything somebody is holding,
 * and the two have to be written the same way to be read together.
 */
export function formatMinutes(minutes: number | null): string {
  if (minutes === null || minutes === 0) return '—'
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}
