// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'

import { useSessionStore } from '@/stores/session-store'
import type { User } from '@/types/session'

import { NoWorkspacePage } from './no-workspace-page'

/**
 * What somebody sees when they belong to no workspace.
 *
 * Onboarding by approval made this screen the ordinary first thing a new
 * account sees rather than an edge case, so it has to tell the two readings of
 * "no workspace" apart. A registration waiting for an administrator has done
 * nothing wrong and has nothing to do; an approved account with no membership
 * is a different situation and deserves different words.
 */

function person(status: string): User {
  return {
    id: 'user-1',
    email: 'ada@example.com',
    firstName: 'Ada',
    lastName: 'Lovelace',
    status,
  }
}

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <NoWorkspacePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('NoWorkspacePage', () => {
  beforeEach(() => {
    useSessionStore.setState({ status: 'authenticated', user: person('PENDING_APPROVAL'), memberships: [] })
  })

  it('tells a waiting registration that an administrator has to approve it', () => {
    renderPage()

    expect(screen.getByRole('heading').textContent).toMatch(/waiting for approval/i)
    // The action is somebody else's, so the copy must not imply a missed step.
    expect(screen.getByText(/administrator has to approve it/i)).toBeTruthy()
  })

  it('names the address so it is obvious which account is waiting', () => {
    renderPage()

    expect(screen.getByText(/ada@example\.com/)).toBeTruthy()
  })

  it('tells an approved account with no membership something different', () => {
    useSessionStore.setState({ status: 'authenticated', user: person('ACTIVE'), memberships: [] })

    renderPage()

    expect(screen.getByRole('heading').textContent).toMatch(/not in a workspace yet/i)
    expect(screen.queryByText(/waiting for approval/i)).toBeNull()
  })

  it('offers only the two things the reader can actually do', () => {
    renderPage()

    expect(screen.getByRole('button', { name: /check again/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /sign out/i })).toBeTruthy()
  })
})
