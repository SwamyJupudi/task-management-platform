import {
  currentMonth,
  dayLabel,
  firstDayOf,
  isDateKey,
  isMonthKey,
  lastDayOf,
  monthGrid,
  monthLabel,
  monthOf,
  shiftMonth,
  today,
  weekdayLabels,
} from './month'

/**
 * The calendar's month arithmetic.
 *
 * Worth pinning down because it is the kind of code that is wrong only at the
 * edges — a leap year, a month that starts on a Sunday, a December that rolls
 * into January — and because the whole module exists to avoid one specific bug:
 * turning a zone-free `LocalDate` into a `Date` and reading it back a day out.
 */

describe('guards', () => {
  it('accepts a real month key and rejects everything else', () => {
    expect(isMonthKey('2026-09')).toBe(true)
    expect(isMonthKey('2026-01')).toBe(true)
    expect(isMonthKey('2026-12')).toBe(true)

    expect(isMonthKey('2026-13')).toBe(false)
    expect(isMonthKey('2026-00')).toBe(false)
    expect(isMonthKey('2026-9')).toBe(false)
    expect(isMonthKey('2026-09-01')).toBe(false)
    expect(isMonthKey('')).toBe(false)
    expect(isMonthKey('nonsense')).toBe(false)
  })

  it('accepts a real date key and rejects everything else', () => {
    expect(isDateKey('2026-09-03')).toBe(true)
    expect(isDateKey('2026-09-31')).toBe(true)

    expect(isDateKey('2026-09-32')).toBe(false)
    expect(isDateKey('2026-09-00')).toBe(false)
    expect(isDateKey('2026-09-3')).toBe(false)
    expect(isDateKey('2026-09')).toBe(false)
    expect(isDateKey('')).toBe(false)
  })
})

describe('month and day boundaries', () => {
  it('reads the month out of a date', () => {
    expect(monthOf('2026-09-03')).toBe('2026-09')
  })

  it('gives the first day of the month', () => {
    expect(firstDayOf('2026-09')).toBe('2026-09-01')
    expect(firstDayOf('2026-02')).toBe('2026-02-01')
  })

  it('gives the last day of every month length', () => {
    expect(lastDayOf('2026-01')).toBe('2026-01-31')
    expect(lastDayOf('2026-04')).toBe('2026-04-30')
    expect(lastDayOf('2026-12')).toBe('2026-12-31')
  })

  it('gets February right in both a common and a leap year', () => {
    expect(lastDayOf('2026-02')).toBe('2026-02-28')
    expect(lastDayOf('2024-02')).toBe('2024-02-29')
    // 1900 was not a leap year; 2000 was.
    expect(lastDayOf('2000-02')).toBe('2000-02-29')
  })
})

describe('shiftMonth', () => {
  it('moves within a year', () => {
    expect(shiftMonth('2026-09', 1)).toBe('2026-10')
    expect(shiftMonth('2026-09', -1)).toBe('2026-08')
  })

  it('rolls over both year boundaries', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
  })

  it('moves more than a year at a time', () => {
    expect(shiftMonth('2026-09', 12)).toBe('2027-09')
    expect(shiftMonth('2026-09', -24)).toBe('2024-09')
  })
})

describe('monthGrid', () => {
  const months = ['2026-01', '2026-02', '2024-02', '2026-09', '2026-11', '2027-02', '2026-08']

  it.each(months)('fills whole weeks for %s', (month) => {
    expect(monthGrid(month).length % 7).toBe(0)
  })

  it.each(months)('starts on a Monday for %s', (month) => {
    const first = monthGrid(month)[0]
    expect(first).toBeDefined()
    // Local midnight, never the ISO form, which would be parsed as UTC.
    expect(new Date(`${first?.date}T00:00:00`).getDay()).toBe(1)
  })

  it.each(months)('holds every day of %s exactly once, in order', (month) => {
    const inMonth = monthGrid(month).filter((day) => day.inMonth)
    const expected = Number(lastDayOf(month).slice(-2))

    expect(inMonth).toHaveLength(expected)
    expect(inMonth.map((day) => day.day)).toEqual(
      Array.from({ length: expected }, (_, index) => index + 1),
    )
  })

  it('pads with neighbouring days rather than leaving holes', () => {
    // September 2026 starts on a Tuesday, so one day of August leads it.
    const grid = monthGrid('2026-09')
    expect(grid[0]?.date).toBe('2026-08-31')
    expect(grid[0]?.inMonth).toBe(false)
    expect(grid[1]?.date).toBe('2026-09-01')
    expect(grid[1]?.inMonth).toBe(true)
  })

  it('drops a trailing week that holds none of the month', () => {
    // February 2027 is exactly four Monday-to-Sunday weeks.
    expect(monthGrid('2027-02')).toHaveLength(28)
    // November 2026 needs six.
    expect(monthGrid('2026-11')).toHaveLength(42)
  })

  it('marks today, and only today', () => {
    const now = today()
    const marked = monthGrid(now.slice(0, 7)).filter((day) => day.isToday)

    expect(marked).toHaveLength(1)
    expect(marked[0]?.date).toBe(now)
  })

  it('marks no day at all in a month that is not this one', () => {
    const distant = shiftMonth(currentMonth(), 6)
    expect(monthGrid(distant).some((day) => day.isToday)).toBe(false)
  })
})

describe('the current month and today', () => {
  it('agree with each other', () => {
    expect(monthOf(today())).toBe(currentMonth())
  })

  it('are read from local components rather than shifted into UTC', () => {
    // Late on the last day of a month in a positive-offset zone, the UTC date
    // is still the previous month. Local components must win.
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 8, 30, 23, 30))

    expect(today()).toBe('2026-09-30')
    expect(currentMonth()).toBe('2026-09')

    vi.useRealTimers()
  })
})

describe('labels', () => {
  it('names a month without being a day out', () => {
    // The first of the month, which is where an ISO parse would slip backwards.
    expect(monthLabel('2026-01')).toContain('2026')
    expect(monthLabel('2026-01')).not.toContain('2025')
  })

  it('names a day without being a day out', () => {
    expect(dayLabel('2026-01-01')).toContain('2026')
    expect(dayLabel('2026-01-01')).not.toContain('2025')
    expect(dayLabel('2026-03-01')).not.toContain('February')
  })

  it('gives seven weekday headings starting on Monday', () => {
    const labels = weekdayLabels()

    expect(labels).toHaveLength(7)
    expect(new Set(labels).size).toBe(7)
    // The 5th of January 2026 is a Monday; the first heading is built from it.
    expect(labels[0]).toBe(new Date(2026, 0, 5).toLocaleDateString(undefined, { weekday: 'short' }))
  })
})
