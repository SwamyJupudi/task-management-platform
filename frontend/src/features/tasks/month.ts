/**
 * Month arithmetic for the calendar, in plain strings.
 *
 * **Every date here is a `YYYY-MM-DD` string and is compared as one.** A task's
 * `dueDate` is a `LocalDate` on the wire — a calendar date with no time and no
 * zone — and the moment it becomes a `Date` it acquires both. `new
 * Date('2026-09-03')` is parsed as UTC midnight, which in any negative-offset
 * zone reads back as the second of September; a grid built that way is a day
 * out for half the world. Strings have no such opinion, so the bucketing and
 * the range the query asks for are done on them, and a `Date` is constructed
 * only to render a label.
 *
 * There is no date library in this project and this does not add one. A month
 * grid is a fortnight's worth of arithmetic that the standard library already
 * does correctly, as long as it is fed local components rather than an ISO
 * string.
 */

/** `2026-09`, the shape the `month` query parameter carries. */
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/

export function isMonthKey(value: string): boolean {
  return MONTH_PATTERN.test(value)
}

/** `2026-09-03`, the shape a date filter carries and a task's `dueDate` has. */
const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/

/**
 * Guards a date that arrived from the query string before it becomes a filter.
 *
 * The shape only. Whether the thirty-first of February exists is the backend's
 * judgement, and duplicating it here would be a second opinion on the same
 * question.
 */
export function isDateKey(value: string): boolean {
  return DATE_PATTERN.test(value)
}

/** Two digits, because `String(9).padStart` reads worse than saying so once. */
function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

/** The month a date string belongs to. */
export function monthOf(date: string): string {
  return date.slice(0, 7)
}

/**
 * The month showing now, from the browser's own clock.
 *
 * Local components rather than `toISOString`, which would shift the date into
 * UTC and could name last month on the first of a month in Auckland.
 */
export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`
}

/** Today, as a `YYYY-MM-DD` string in the reader's own zone. */
export function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** The month `delta` months away. Rolls the year over, which `Date` does for us. */
export function shiftMonth(month: string, delta: number): string {
  const [year, index] = month.split('-').map(Number)
  const date = new Date(year as number, (index as number) - 1 + delta, 1)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}

/** The first day of the month, inclusive — what `dueAfter` is given. */
export function firstDayOf(month: string): string {
  return `${month}-01`
}

/**
 * The last day of the month, inclusive — what `dueBefore` is given.
 *
 * Day zero of the next month is the last day of this one, which is the one
 * piece of `Date` arithmetic worth keeping rather than counting days per month.
 */
export function lastDayOf(month: string): string {
  const [year, index] = month.split('-').map(Number)
  const date = new Date(year as number, index as number, 0)
  return `${month}-${pad(date.getDate())}`
}

/** One cell of the grid. */
export interface CalendarDay {
  /** `YYYY-MM-DD`. The key tasks are bucketed under. */
  date: string
  /** The day number, for the label. */
  day: number
  /** False for the leading and trailing days that pad the grid to whole weeks. */
  inMonth: boolean
  isToday: boolean
}

/**
 * The grid: whole weeks, Monday first, padded at both ends.
 *
 * Monday rather than Sunday because the platform's own week starts there —
 * `TrendGranularity.WEEK` buckets on `previousOrSame(MONDAY)` — and a calendar
 * that disagreed with the reports beside it would be its own small confusion.
 *
 * Padding days belong to the neighbouring months and are drawn muted. They are
 * included rather than left blank so every row has seven cells and the grid
 * needs no special case for its corners. Tasks are never bucketed into them:
 * the query only asked for this month, so there is nothing to put there.
 */
export function monthGrid(month: string): CalendarDay[] {
  const [year, index] = month.split('-').map(Number)
  const firstOfMonth = new Date(year as number, (index as number) - 1, 1)

  // getDay is Sunday-zero; this shifts it to Monday-zero.
  const lead = (firstOfMonth.getDay() + 6) % 7

  const start = new Date(firstOfMonth)
  start.setDate(start.getDate() - lead)

  const now = today()
  const days: CalendarDay[] = []

  // Six weeks covers every month: 31 days starting on a Sunday needs 37 cells.
  // Trimmed below to whatever the month actually spans.
  for (let offset = 0; offset < 42; offset += 1) {
    const date = new Date(start)
    date.setDate(start.getDate() + offset)

    const key = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    days.push({
      date: key,
      day: date.getDate(),
      inMonth: monthOf(key) === month,
      isToday: key === now,
    })
  }

  // Drop a trailing week that holds none of this month, which happens whenever
  // the month fits in five rows.
  const weeks = days.length / 7
  for (let week = weeks - 1; week >= 0; week -= 1) {
    const slice = days.slice(week * 7, week * 7 + 7)
    if (slice.some((day) => day.inMonth)) {
      return days.slice(0, week * 7 + 7)
    }
  }
  return days
}

/** The month written for a person: "September 2026". */
export function monthLabel(month: string): string {
  const [year, index] = month.split('-').map(Number)
  return new Date(year as number, (index as number) - 1, 1).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  })
}

/**
 * The weekday headings, in the reader's own language.
 *
 * Built from a known Monday rather than hard-coded, so the grid reads correctly
 * wherever it is opened. The 5th of January 2026 is a Monday.
 */
export function weekdayLabels(): string[] {
  const monday = new Date(2026, 0, 5)
  return Array.from({ length: 7 }, (_, offset) => {
    const date = new Date(monday)
    date.setDate(monday.getDate() + offset)
    return date.toLocaleDateString(undefined, { weekday: 'short' })
  })
}

/** A day written for a person: "3 September". Used in the day heading. */
export function dayLabel(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year as number, (month as number) - 1, day as number).toLocaleDateString(
    undefined,
    { day: 'numeric', month: 'long', year: 'numeric' },
  )
}
