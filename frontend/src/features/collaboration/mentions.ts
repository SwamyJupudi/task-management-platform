import type { Mention } from './types'

/**
 * The mention token, and how a comment body is split around it.
 *
 * The canonical form is `@[user:<uuid>]`, written into the body by whatever
 * composed it and read back out by the backend's `MentionParser`. The pattern
 * here is the same one, deliberately strict about the uuid shape: a looser
 * match would let `@[user:whatever]` through to be rejected later, so the
 * composer could produce something the server refuses.
 *
 * Nothing about a person is stored in the body beyond the identifier, which is
 * what lets a rename show up on the next read instead of leaving a stale name
 * in somebody's words.
 */

const UUID = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'

/** Global, because rendering walks every token in the body. */
const MENTION = new RegExp(`@\\[user:(${UUID})\\]`, 'g')

/** The token to insert when somebody is chosen from the picker. */
export function mentionToken(userId: string): string {
  return `@[user:${userId}]`
}

/** One piece of a comment body: either plain words or somebody named. */
export type BodySegment =
  { kind: 'text'; text: string } | { kind: 'mention'; userId: string; label: string }

/**
 * Splits a body into the pieces a renderer draws.
 *
 * A token whose person the backend did not resolve — someone removed from the
 * workspace since — is rendered as a mention with the raw identifier shortened
 * rather than dropped or left as the raw token. The words still name somebody;
 * pretending otherwise would edit what was written.
 */
export function parseBody(body: string, mentions: readonly Mention[]): BodySegment[] {
  const byId = new Map(mentions.map((mention) => [mention.userId.toLowerCase(), mention.name]))
  const segments: BodySegment[] = []

  let lastIndex = 0
  // `matchAll` needs the global flag and gives a fresh iterator each call, so
  // there is no shared `lastIndex` to reset between bodies.
  for (const match of body.matchAll(MENTION)) {
    const index = match.index
    const userId = match[1]
    if (index === undefined || userId === undefined) continue

    if (index > lastIndex) {
      segments.push({ kind: 'text', text: body.slice(lastIndex, index) })
    }
    segments.push({
      kind: 'mention',
      userId,
      label: byId.get(userId.toLowerCase()) ?? `${userId.slice(0, 8)}…`,
    })
    lastIndex = index + match[0].length
  }

  if (lastIndex < body.length) {
    segments.push({ kind: 'text', text: body.slice(lastIndex) })
  }

  return segments
}

/**
 * Inserts a mention at the caret, and reports where the caret should land.
 *
 * Returned rather than applied, so the caller owns the input's state and this
 * stays a pure function that can be reasoned about without a DOM.
 */
export function insertMention(
  body: string,
  caret: number,
  userId: string,
): { body: string; caret: number } {
  const token = mentionToken(userId)
  const before = body.slice(0, caret)
  const after = body.slice(caret)

  // A space after the token, and one before it unless the line already ends in
  // whitespace, so two mentions in a row do not run together.
  const prefix = before === '' || /\s$/.test(before) ? '' : ' '
  const inserted = `${prefix}${token} `

  return { body: `${before}${inserted}${after}`, caret: caret + inserted.length }
}
