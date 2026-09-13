import {
  readDate,
  readEnum,
  readEnumList,
  readId,
  readPage,
  readSort,
  withParam,
} from './url-state'

/**
 * Reading a report's filters out of the query string, and writing them back.
 *
 * A query string is user input — typed, truncated, or left over from an older
 * version of a screen — so the value of these guards is entirely in what they
 * refuse. A forwarded rubbish value turns a mistyped link into a 400.
 */

const params = (query: string) => new URLSearchParams(query)

const UUID = '2f9c6f4e-1b3a-4c5d-8e7f-0a1b2c3d4e5f'

describe('readId', () => {
  it('accepts a UUID in either case', () => {
    expect(readId(params(`teamId=${UUID}`), 'teamId')).toBe(UUID)
    expect(readId(params(`teamId=${UUID.toUpperCase()}`), 'teamId')).toBe(UUID.toUpperCase())
  })

  it('refuses anything that is not one', () => {
    expect(readId(params('teamId=not-a-uuid'), 'teamId')).toBeUndefined()
    expect(readId(params('teamId='), 'teamId')).toBeUndefined()
    expect(readId(params(`teamId=${UUID}x`), 'teamId')).toBeUndefined()
    expect(readId(params(''), 'teamId')).toBeUndefined()
  })
})

describe('readDate', () => {
  it('accepts the shape the backend parses', () => {
    expect(readDate(params('from=2026-09-01'), 'from')).toBe('2026-09-01')
  })

  it('refuses a malformed one', () => {
    expect(readDate(params('from=2026-9-1'), 'from')).toBeUndefined()
    expect(readDate(params('from=yesterday'), 'from')).toBeUndefined()
    expect(readDate(params(''), 'from')).toBeUndefined()
  })

  it('leaves the calendar to the backend', () => {
    // Shape only: whether the 31st of February exists is the server's
    // judgement, and a second opinion here would be a second rule.
    expect(readDate(params('from=2026-02-31'), 'from')).toBe('2026-02-31')
  })
})

describe('readEnum', () => {
  const accepts = (value: string) => ['DAY', 'WEEK', 'MONTH'].includes(value)

  it('passes a value the set holds', () => {
    expect(readEnum(params('granularity=WEEK'), 'granularity', accepts)).toBe('WEEK')
  })

  it('drops one it does not, rather than forwarding a 400', () => {
    expect(readEnum(params('granularity=HOURLY'), 'granularity', accepts)).toBeUndefined()
    expect(readEnum(params('granularity=week'), 'granularity', accepts)).toBeUndefined()
    expect(readEnum(params(''), 'granularity', accepts)).toBeUndefined()
  })
})

describe('readEnumList', () => {
  const accepts = (value: string) => ['ACTIVE', 'PLANNING', 'ARCHIVED'].includes(value)

  it('collects every repeated value the set holds', () => {
    expect(readEnumList(params('status=ACTIVE&status=PLANNING'), 'status', accepts)).toEqual([
      'ACTIVE',
      'PLANNING',
    ])
  })

  it('filters out the ones it does not hold', () => {
    expect(readEnumList(params('status=ACTIVE&status=NOPE'), 'status', accepts)).toEqual(['ACTIVE'])
  })

  it('is undefined rather than empty when nothing survives', () => {
    // Undefined is what the client drops from the query string; an empty array
    // would be sent as no parameter anyway but reads as "filtered to nothing".
    expect(readEnumList(params('status=NOPE'), 'status', accepts)).toBeUndefined()
    expect(readEnumList(params(''), 'status', accepts)).toBeUndefined()
  })
})

describe('readPage', () => {
  it('translates one-based in the URL to zero-based on the wire', () => {
    expect(readPage(params('page=1'))).toBe(0)
    expect(readPage(params('page=4'))).toBe(3)
  })

  it('falls back to the first page for anything unreadable', () => {
    expect(readPage(params(''))).toBe(0)
    expect(readPage(params('page=0'))).toBe(0)
    expect(readPage(params('page=-2'))).toBe(0)
    expect(readPage(params('page=nonsense'))).toBe(0)
    expect(readPage(params('page=Infinity'))).toBe(0)
  })

  it('floors a fractional page rather than sending one', () => {
    expect(readPage(params('page=3.7'))).toBe(2)
  })
})

describe('readSort', () => {
  const options = [{ value: 'progress,desc' }, { value: 'name,asc' }]

  it('passes a sort the allowlist holds', () => {
    expect(readSort(params('sort=name,asc'), options, 'progress,desc')).toBe('name,asc')
  })

  it('falls back for one it does not, which the backend answers 400 for', () => {
    expect(readSort(params('sort=secret,desc'), options, 'progress,desc')).toBe('progress,desc')
    expect(readSort(params(''), options, 'progress,desc')).toBe('progress,desc')
  })
})

describe('withParam', () => {
  it('sets a value', () => {
    expect(withParam(params('a=1'), 'b', '2').toString()).toBe('a=1&b=2')
  })

  it('replaces rather than appends', () => {
    expect(withParam(params('a=1'), 'a', '2').toString()).toBe('a=2')
  })

  it('removes a value that is undefined or empty', () => {
    expect(withParam(params('a=1&b=2'), 'a', undefined).toString()).toBe('b=2')
    expect(withParam(params('a=1&b=2'), 'a', '').toString()).toBe('b=2')
  })

  it('repeats an array, and clears it first', () => {
    const next = withParam(params('status=OLD'), 'status', ['ACTIVE', 'PLANNING'])
    expect(next.getAll('status')).toEqual(['ACTIVE', 'PLANNING'])
  })

  it('returns to the first page whenever a filter changes', () => {
    // Staying put would leave a reader on page four of a narrower result that
    // may only have two, and the backend answers an empty page rather than an
    // error — a blank screen with no explanation.
    expect(withParam(params('page=4&a=1'), 'a', '2').has('page')).toBe(false)
  })

  it('keeps the page when the page itself is what changed', () => {
    expect(withParam(params('a=1'), 'page', '3').get('page')).toBe('3')
  })

  it('does not mutate the params it was given', () => {
    const original = params('a=1')
    withParam(original, 'b', '2')
    expect(original.toString()).toBe('a=1')
  })
})
