import { formatMinutes, relativeTime } from './datetime'

/**
 * The two formatters shared across features.
 *
 * Both are boundary logic: `relativeTime` switches units four times and then
 * gives up and shows a date, and `formatMinutes` changes shape at the hour.
 * Every one of those edges is a place to be off by one.
 */

const NOW = new Date('2026-09-13T12:00:00Z')

/** `n` units before the fixed now, as the ISO string the API would send. */
function ago(milliseconds: number): string {
  return new Date(NOW.getTime() - milliseconds).toISOString()
}

const SECOND = 1000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

describe('relativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('says "just now" under a minute', () => {
    expect(relativeTime(ago(0))).toBe('just now')
    expect(relativeTime(ago(59 * SECOND))).toBe('just now')
  })

  it('counts minutes up to an hour', () => {
    expect(relativeTime(ago(MINUTE))).toBe('1m ago')
    expect(relativeTime(ago(45 * MINUTE))).toBe('45m ago')
  })

  it('counts hours up to a day', () => {
    expect(relativeTime(ago(HOUR))).toBe('1h ago')
    expect(relativeTime(ago(5 * HOUR))).toBe('5h ago')
  })

  it('counts days up to a week', () => {
    expect(relativeTime(ago(DAY))).toBe('1d ago')
    expect(relativeTime(ago(6 * DAY))).toBe('6d ago')
  })

  it('switches to a date past a week, because a growing day count stops helping', () => {
    const older = relativeTime(ago(30 * DAY))

    expect(older).not.toContain('ago')
    expect(older.length).toBeGreaterThan(0)
  })

  it('returns an empty string for something that is not a date', () => {
    // Rendered as nothing rather than "Invalid Date".
    expect(relativeTime('not a date')).toBe('')
    expect(relativeTime('')).toBe('')
  })
})

describe('formatMinutes', () => {
  it('shows a dash for nothing recorded', () => {
    expect(formatMinutes(null)).toBe('—')
    expect(formatMinutes(0)).toBe('—')
  })

  it('shows bare minutes under an hour', () => {
    expect(formatMinutes(1)).toBe('1m')
    expect(formatMinutes(59)).toBe('59m')
  })

  it('shows whole hours without a stray zero', () => {
    expect(formatMinutes(60)).toBe('1h')
    expect(formatMinutes(120)).toBe('2h')
  })

  it('shows hours and minutes together', () => {
    expect(formatMinutes(90)).toBe('1h 30m')
    expect(formatMinutes(485)).toBe('8h 5m')
  })
})
