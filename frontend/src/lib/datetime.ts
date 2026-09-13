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
