import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * What every test file gets before it runs.
 *
 * One thing only. `cleanup` unmounts whatever a test rendered; Testing Library
 * does this itself when the runner exposes a global `afterEach`, but saying it
 * here means the suite does not depend on that detail holding.
 *
 * There is deliberately no `jest-dom`. Its matchers read well but it is a fifth
 * dependency for sugar, and `expect(element).not.toBeNull()` says the same
 * thing as `toBeInTheDocument` without one.
 */
afterEach(() => {
  cleanup()
})
