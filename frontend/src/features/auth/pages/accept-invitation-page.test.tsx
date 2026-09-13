// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

import { ApiError } from '@/lib/api'
import { useSessionStore } from '@/stores/session-store'

import * as authApi from '../api'
import type { InvitationPreview } from '../types'

import { AcceptInvitationPage } from './accept-invitation-page'

/**
 * The invitation screen, which is a branch matrix more than a form.
 *
 * Six outcomes, and choosing the wrong one is how an invitation quietly stops
 * working: an expired link that says "withdrawn", a signed-in stranger offered
 * a button the backend will refuse, or an account that already exists being
 * shown a password form it must not have.
 *
 * The preview is stubbed at the API module. What is asserted is which branch
 * the screen takes for a given answer, not how the request was made.
 */

const PREVIEW: InvitationPreview = {
  workspaceName: 'Acme Platform',
  email: 'invitee@example.com',
  roleSlug: 'EMPLOYEE',
  accountExists: false,
}

function signedInAs(email: string | null) {
  if (email === null) {
    useSessionStore.setState({ status: 'anonymous', user: null })
    return
  }
  useSessionStore.setState({
    status: 'authenticated',
    user: { id: 'u-1', email, firstName: 'Ada', lastName: 'Lovelace', status: 'ACTIVE' },
    memberships: [],
    accessToken: 'token',
  })
}

function renderPage(search: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/invitations/accept${search}`]}>
        <Routes>
          <Route path="/invitations/accept" element={<AcceptInvitationPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  useSessionStore.getState().clear()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('without a usable link', () => {
  it('explains itself when the URL carries no token', async () => {
    const preview = vi.spyOn(authApi, 'previewInvitation')

    renderPage('')

    expect(await screen.findByText(/needs an invitation link/i)).toBeDefined()
    // Nothing to preview, so nothing is asked for.
    expect(preview).not.toHaveBeenCalled()
  })
})

describe('when the token is refused', () => {
  it('says expired for TOKEN_EXPIRED, which can be sent again', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockRejectedValue(
      new ApiError({
        status: 401,
        code: 'TOKEN_EXPIRED',
        message: 'That token has expired. Please request a new one.',
        requestId: 'req-1',
        violations: [],
      }),
    )

    renderPage('?token=abc')

    // The heading specifically: the backend's own message says "expired" too,
    // and matching loosely would find both.
    expect(await screen.findByText('That invitation has expired')).toBeDefined()
  })

  it('says cannot be used for TOKEN_INVALID, covering all three reasons', async () => {
    // Withdrawn, already accepted, or never real — the backend collapses them
    // on purpose, so the wording must not guess between them.
    vi.spyOn(authApi, 'previewInvitation').mockRejectedValue(
      new ApiError({
        status: 401,
        code: 'TOKEN_INVALID',
        message: 'That token is not valid. Please request a new one.',
        requestId: 'req-1',
        violations: [],
      }),
    )

    renderPage('?token=abc')

    expect(await screen.findByText('That invitation cannot be used')).toBeDefined()
    expect(screen.queryByText('That invitation has expired')).toBeNull()
  })

  it('shows the backend message and its request id', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockRejectedValue(
      new ApiError({
        status: 401,
        code: 'TOKEN_INVALID',
        message: 'That token is not valid. Please request a new one.',
        requestId: 'req-abcdef',
        violations: [],
      }),
    )

    renderPage('?token=abc')

    expect(await screen.findByText(/That token is not valid/)).toBeDefined()
    expect(screen.queryByText(/req-abcdef/)).not.toBeNull()
  })
})

describe('when no account exists for the invited address', () => {
  it('offers the form that creates one', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue(PREVIEW)
    signedInAs(null)

    renderPage('?token=abc')

    expect(await screen.findByLabelText(/first name/i)).toBeDefined()
    expect(screen.queryByLabelText(/^password$/i)).not.toBeNull()
    expect(screen.queryByLabelText(/confirm password/i)).not.toBeNull()
  })

  it('shows the workspace, the address and the role from the preview', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue(PREVIEW)
    signedInAs(null)

    renderPage('?token=abc')

    expect(await screen.findByText('Acme Platform')).toBeDefined()
    expect(screen.queryByText('invitee@example.com')).not.toBeNull()
    expect(screen.queryByText('EMPLOYEE')).not.toBeNull()
  })
})

describe('when an account already exists', () => {
  const existing: InvitationPreview = { ...PREVIEW, accountExists: true }

  it('asks an anonymous visitor to sign in, and offers no password form', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue(existing)
    signedInAs(null)

    renderPage('?token=abc')

    expect(await screen.findByText(/sign in as invitee@example.com/i)).toBeDefined()
    // Creating an account here would be creating a second one for one address.
    expect(screen.queryByLabelText(/confirm password/i)).toBeNull()
  })

  it('offers the join button when the session is the invited account', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue(existing)
    signedInAs('invitee@example.com')

    renderPage('?token=abc')

    expect(await screen.findByRole('button', { name: /join acme platform/i })).toBeDefined()
    expect(screen.queryByLabelText(/confirm password/i)).toBeNull()
  })

  it('matches the address case-insensitively', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue(existing)
    signedInAs('Invitee@Example.com')

    renderPage('?token=abc')

    expect(await screen.findByRole('button', { name: /join acme platform/i })).toBeDefined()
  })

  it('explains the mismatch when the session belongs to somebody else', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue(existing)
    signedInAs('someone.else@example.com')

    renderPage('?token=abc')

    // The backend would answer 401 here; saying so is kinder than a button
    // that cannot work.
    expect(await screen.findByText(/signed in as somebody else/i)).toBeDefined()
    expect(screen.queryByRole('button', { name: /join acme platform/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /sign out/i })).not.toBeNull()
  })
})

describe('accepting', () => {
  it('sends the token alone for an account that already exists', async () => {
    vi.spyOn(authApi, 'previewInvitation').mockResolvedValue({ ...PREVIEW, accountExists: true })
    const accept = vi.spyOn(authApi, 'acceptInvitation').mockResolvedValue({ workspaceId: 'ws-1' })
    vi.spyOn(authApi, 'me').mockResolvedValue({
      user: {
        id: 'u-1',
        email: 'invitee@example.com',
        firstName: 'Ada',
        lastName: 'Lovelace',
        status: 'ACTIVE',
      },
      platformRole: null,
      platformPermissions: [],
      memberships: [],
    })
    signedInAs('invitee@example.com')

    renderPage('?token=the-token')
    const join = await screen.findByRole('button', { name: /join acme platform/i })
    join.click()

    await waitFor(() => expect(accept).toHaveBeenCalled())

    // Authenticated, and no password: the session proves who is accepting.
    expect(accept).toHaveBeenCalledWith({ token: 'the-token' }, true)
  })
})
