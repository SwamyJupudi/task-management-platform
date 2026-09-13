import { createWorkspaceSchema, suggestSlug } from './schemas'

/**
 * The workspace creation form's rules.
 *
 * `suggestSlug` is the one worth testing hardest: it produces a value that gets
 * submitted, and the backend refuses anything that is not lowercase words
 * separated by single hyphens. A suggestion the server rejects would make the
 * form feel broken at the only moment a slug can ever be chosen.
 */

/** The pattern `CreateWorkspaceRequest` declares, copied here to assert against. */
const BACKEND_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/

describe('suggestSlug', () => {
  it('lowercases and hyphenates', () => {
    expect(suggestSlug('Platform Team')).toBe('platform-team')
  })

  it('folds accents rather than dropping the letters', () => {
    expect(suggestSlug('Ünicode Ärger')).toBe('unicode-arger')
  })

  it('collapses runs of anything unusable into one hyphen', () => {
    expect(suggestSlug('  Spaced   Out  ')).toBe('spaced-out')
    expect(suggestSlug('double--hyphen')).toBe('double-hyphen')
    expect(suggestSlug('under_score')).toBe('under-score')
  })

  it('trims leading and trailing hyphens', () => {
    expect(suggestSlug('-leading')).toBe('leading')
    expect(suggestSlug('trailing-')).toBe('trailing')
  })

  it('keeps digits', () => {
    expect(suggestSlug('Team 42')).toBe('team-42')
  })

  it('is empty when there is nothing usable, rather than a bare hyphen', () => {
    expect(suggestSlug('...')).toBe('')
    expect(suggestSlug('')).toBe('')
  })

  it('truncates to the length the backend allows, without a trailing hyphen', () => {
    const long = suggestSlug('a'.repeat(80))
    expect(long.length).toBeLessThanOrEqual(60)

    const cut = suggestSlug(`${'a'.repeat(59)} tail`)
    expect(cut.endsWith('-')).toBe(false)
  })

  it.each([
    'Platform Team',
    '  Spaced   Out  ',
    'Ünicode Ärger',
    'ALL CAPS',
    'under_score',
    'double--hyphen',
    'trailing-',
    '-leading',
    'a'.repeat(80),
    'Team 42!',
    'Mix3d C4se',
    'a/b\\c:d',
  ])('never suggests something the backend would refuse, for %j', (name) => {
    const slug = suggestSlug(name)
    // Empty is allowed through to the required-field rule instead.
    expect(slug === '' || BACKEND_PATTERN.test(slug)).toBe(true)
  })
})

describe('createWorkspaceSchema', () => {
  it('accepts a well-formed pair', () => {
    expect(createWorkspaceSchema.safeParse({ name: 'Platform', slug: 'platform' }).success).toBe(
      true,
    )
  })

  it('requires both fields', () => {
    expect(createWorkspaceSchema.safeParse({ name: '', slug: 'platform' }).success).toBe(false)
    expect(createWorkspaceSchema.safeParse({ name: 'Platform', slug: '' }).success).toBe(false)
    expect(createWorkspaceSchema.safeParse({ name: '   ', slug: 'platform' }).success).toBe(false)
  })

  it('mirrors the backend length bounds', () => {
    expect(createWorkspaceSchema.safeParse({ name: 'x'.repeat(121), slug: 'ok' }).success).toBe(
      false,
    )
    expect(
      createWorkspaceSchema.safeParse({ name: 'Platform', slug: 'a'.repeat(61) }).success,
    ).toBe(false)
  })

  it.each(['Bad Slug', 'trailing-', '-leading', 'double--hyphen', 'under_score', 'UPPER'])(
    'refuses %j before the request is sent',
    (slug) => {
      expect(createWorkspaceSchema.safeParse({ name: 'Platform', slug }).success).toBe(false)
    },
  )
})
