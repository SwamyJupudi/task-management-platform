import { insertMention, mentionToken, parseBody } from './mentions'
import type { Mention } from './types'

/**
 * Mention tokens: writing them, reading them back, and inserting one.
 *
 * The token is a contract shared with the backend's `MentionParser`, so the
 * shape matters exactly. `parseBody` is what a comment is rendered through, and
 * a bug there either loses somebody's words or shows a raw token in them.
 */

const ADA = '2f9c6f4e-1b3a-4c5d-8e7f-0a1b2c3d4e5f'
const GRACE = '7a8b9c0d-1e2f-3a4b-5c6d-7e8f9a0b1c2d'

const MENTIONS: Mention[] = [
  { userId: ADA, name: 'Ada Lovelace', email: 'ada@example.com' },
  { userId: GRACE, name: 'Grace Hopper', email: 'grace@example.com' },
]

describe('mentionToken', () => {
  it('writes the canonical form the backend parses', () => {
    expect(mentionToken(ADA)).toBe(`@[user:${ADA}]`)
  })
})

describe('parseBody', () => {
  it('returns a body with no mentions as one piece of text', () => {
    expect(parseBody('just words', MENTIONS)).toEqual([{ kind: 'text', text: 'just words' }])
  })

  it('returns nothing for an empty body', () => {
    expect(parseBody('', MENTIONS)).toEqual([])
  })

  it('splits text around a mention and resolves the name', () => {
    expect(parseBody(`hello ${mentionToken(ADA)} there`, MENTIONS)).toEqual([
      { kind: 'text', text: 'hello ' },
      { kind: 'mention', userId: ADA, label: 'Ada Lovelace' },
      { kind: 'text', text: ' there' },
    ])
  })

  it('handles a mention at the very start and at the very end', () => {
    expect(parseBody(`${mentionToken(ADA)} first`, MENTIONS)[0]?.kind).toBe('mention')

    const trailing = parseBody(`last ${mentionToken(ADA)}`, MENTIONS)
    expect(trailing).toHaveLength(2)
    expect(trailing[1]?.kind).toBe('mention')
  })

  it('handles two mentions running together', () => {
    const segments = parseBody(`${mentionToken(ADA)}${mentionToken(GRACE)}`, MENTIONS)

    expect(segments).toHaveLength(2)
    expect(segments.every((segment) => segment.kind === 'mention')).toBe(true)
  })

  it('matches the identifier case-insensitively', () => {
    const segments = parseBody(`@[user:${ADA.toUpperCase()}]`, MENTIONS)

    expect(segments[0]).toMatchObject({ kind: 'mention', label: 'Ada Lovelace' })
  })

  it('shortens an unresolved identifier rather than dropping the mention', () => {
    // Somebody removed from the workspace since the comment was written. The
    // words still name them; editing them out would change what was said.
    const segments = parseBody(`hi ${mentionToken(GRACE)}`, [MENTIONS[0] as Mention])

    expect(segments[1]).toMatchObject({ kind: 'mention', userId: GRACE })
    expect((segments[1] as { label: string }).label).toContain(GRACE.slice(0, 8))
  })

  it('leaves a malformed token as plain text', () => {
    // The pattern is strict about the uuid, so a composer cannot produce
    // something the server would then refuse.
    const body = '@[user:whatever] and @[user:123]'
    expect(parseBody(body, MENTIONS)).toEqual([{ kind: 'text', text: body }])
  })

  it('can be called repeatedly without a shared regex position', () => {
    // The pattern is global; `matchAll` gives a fresh iterator, but a bug here
    // would show up only on the second call.
    const body = `a ${mentionToken(ADA)} b`
    expect(parseBody(body, MENTIONS)).toEqual(parseBody(body, MENTIONS))
  })
})

describe('insertMention', () => {
  it('inserts at the caret and reports where it lands', () => {
    const result = insertMention('hello world', 5, ADA)

    expect(result.body).toBe(`hello ${mentionToken(ADA)}  world`)
    expect(result.body.slice(0, result.caret)).toBe(`hello ${mentionToken(ADA)} `)
  })

  it('adds no leading space at the start of an empty body', () => {
    expect(insertMention('', 0, ADA).body).toBe(`${mentionToken(ADA)} `)
  })

  it('adds no leading space when the text already ends in whitespace', () => {
    expect(insertMention('hi ', 3, ADA).body).toBe(`hi ${mentionToken(ADA)} `)
  })

  it('adds one when it does not, so two mentions do not run together', () => {
    expect(insertMention('hi', 2, ADA).body).toBe(`hi ${mentionToken(ADA)} `)
  })

  it('always leaves the caret after the trailing space', () => {
    const result = insertMention('hi', 2, ADA)
    expect(result.body[result.caret - 1]).toBe(' ')
  })
})
