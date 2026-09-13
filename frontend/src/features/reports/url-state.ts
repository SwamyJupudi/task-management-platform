/**
 * Reading a report's filters out of the query string, and writing them back.
 *
 * Filters live in the URL rather than in component state so a report somebody
 * has narrowed is a link they can send: it survives a reload, a bookmark and a
 * paste into a chat window, and two tabs can hold two different views of the
 * same report without fighting over one store.
 *
 * Everything here is defensive about what it reads. A query string is user
 * input — typed, truncated, or left over from an older version of a screen —
 * so a value that is not one of the things the endpoint accepts is dropped
 * rather than forwarded. Sending it on would turn a mistyped link into a 400.
 */

/** `YYYY-MM-DD`, and a real date rather than the thirty-first of February. */
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * A UUID, loosely: eight-four-four-four-twelve hex.
 *
 * Loose because the check exists to keep rubbish out of a query parameter the
 * backend would answer 400 for, not to validate a version or a variant.
 */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** A filter identifier from the URL, or nothing if it is not one. */
export function readId(params: URLSearchParams, key: string): string | undefined {
  const value = params.get(key)
  return value !== null && UUID.test(value) ? value : undefined
}

/**
 * A date from the URL, or nothing.
 *
 * The shape is checked and the calendar is not. `2025-02-31` is refused by the
 * backend's own parser with a message about the date; duplicating that
 * judgement here would mean two opinions about what a date is.
 */
export function readDate(params: URLSearchParams, key: string): string | undefined {
  const value = params.get(key)
  return value !== null && ISO_DATE.test(value) ? value : undefined
}

/** A value from a closed set, or nothing. */
export function readEnum(
  params: URLSearchParams,
  key: string,
  accepts: (value: string) => boolean,
): string | undefined {
  const value = params.get(key)
  return value !== null && accepts(value) ? value : undefined
}

/** Every value of a repeated key that the set accepts, or nothing for none. */
export function readEnumList(
  params: URLSearchParams,
  key: string,
  accepts: (value: string) => boolean,
): string[] | undefined {
  const values = params.getAll(key).filter(accepts)
  return values.length === 0 ? undefined : values
}

/**
 * The page number, as the API counts them.
 *
 * One-based in the URL and zero-based on the wire, which is the one translation
 * this module exists to hide. Anything unreadable is the first page rather than
 * an error: a bad page number is not worth a screen of its own.
 */
export function readPage(params: URLSearchParams): number {
  const raw = Number(params.get('page') ?? '1')
  return Number.isFinite(raw) && raw >= 1 ? Math.floor(raw) - 1 : 0
}

/** The sort, if it is one the endpoint's allowlist holds, or the default. */
export function readSort(
  params: URLSearchParams,
  options: readonly { value: string }[],
  fallback: string,
): string {
  const value = params.get('sort')
  return value !== null && options.some((option) => option.value === value) ? value : fallback
}

/**
 * Replaces one parameter, dropping it when there is nothing to say.
 *
 * Changing a filter also returns to the first page. Staying put would leave a
 * reader on page four of a narrower result that may only have two, and the
 * backend would answer an empty page rather than an error — a blank screen with
 * no explanation, which is the worst of the three possible behaviours.
 */
export function withParam(
  params: URLSearchParams,
  key: string,
  value: string | string[] | undefined,
): URLSearchParams {
  const next = new URLSearchParams(params)
  next.delete(key)

  if (Array.isArray(value)) {
    for (const item of value) next.append(key, item)
  } else if (value !== undefined && value !== '') {
    next.set(key, value)
  }

  if (key !== 'page') next.delete('page')
  return next
}
