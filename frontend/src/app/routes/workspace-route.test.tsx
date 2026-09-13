// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

import { useSessionStore } from '@/stores/session-store'
import type { Membership } from '@/types/session'

import { WorkspaceRoute } from './workspace-route'

/**
 * How a slug in the URL becomes the active workspace.
 *
 * The tenant boundary starts here. `/w/:workspaceSlug` resolves against the
 * memberships `/auth/me` returned, so a slug that is not one of the caller's
 * must never render the shell — whatever permissions they hold elsewhere. The
 * backend refuses the underlying requests regardless, but a screen that drew
 * the previous workspace's navigation under a new workspace's name would be its
 * own kind of wrong.
 */

const ACME: Membership = {
  workspaceId: 'ws-acme',
  workspaceName: 'Acme Platform',
  workspaceSlug: 'acme',
  roleSlug: 'ADMIN',
  roleName: 'Admin',
}

const SIDE: Membership = {
  workspaceId: 'ws-side',
  workspaceName: 'Side Ventures',
  workspaceSlug: 'side',
  roleSlug: 'EMPLOYEE',
  roleName: 'Employee',
}

function signedIn(memberships: Membership[], activeWorkspaceId: string | null = null) {
  useSessionStore.setState({
    status: 'authenticated',
    user: {
      id: 'u-1',
      email: 'ada@example.com',
      firstName: 'Ada',
      lastName: 'Lovelace',
      status: 'ACTIVE',
    },
    platformRole: null,
    platformPermissions: [],
    memberships,
    activeWorkspaceId,
    workspacePermissions: [],
    workspacePermissionsLoaded: false,
    accessToken: 'token',
  })
}

/**
 * A provider is needed because the no-workspace screen offers a sign-out, and
 * that is a mutation. Nothing here makes a request; the client only has to
 * exist.
 */
function renderAt(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/w/:workspaceSlug" element={<WorkspaceRoute />}>
            <Route index element={<p>inside the workspace</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const inside = () => screen.queryByText('inside the workspace')

beforeEach(() => {
  useSessionStore.getState().clear()
})

describe('WorkspaceRoute', () => {
  it('renders the workspace once the slug matches a membership', () => {
    // Already settled on this workspace, which is the steady state.
    signedIn([ACME], 'ws-acme')
    renderAt('/w/acme')

    expect(inside()).not.toBeNull()
  })

  it('sets the active workspace from the slug', () => {
    signedIn([ACME, SIDE], 'ws-acme')
    renderAt('/w/side')

    expect(useSessionStore.getState().activeWorkspaceId).toBe('ws-side')
  })

  it('switches cleanly from one of the caller’s workspaces to another', () => {
    // The component holds at a spinner for the render between the slug
    // changing and the store catching up, which is what stops one workspace's
    // navigation being drawn under another's name. That frame is not
    // observable through Testing Library, which flushes effects — so what is
    // asserted here is the outcome: the right workspace, settled.
    signedIn([ACME, SIDE], 'ws-acme')
    renderAt('/w/side')

    expect(useSessionStore.getState().activeWorkspaceId).toBe('ws-side')
    expect(inside()).not.toBeNull()
  })

  it('refuses a slug that is not one of the caller’s', () => {
    signedIn([ACME], 'ws-acme')
    renderAt('/w/somebody-elses')

    expect(inside()).toBeNull()
    // Reported as missing, which is also how the backend answers.
    expect(screen.queryByText(/somebody-elses/)).not.toBeNull()
  })

  it('never retains the previous workspace when the slug stops matching', () => {
    // A fresh mount rather than a rerender: `MemoryRouter` reads
    // `initialEntries` once, so rerendering it would leave the old location in
    // place and prove nothing.
    signedIn([ACME], 'ws-acme')
    const first = renderAt('/w/acme')
    expect(inside()).not.toBeNull()
    first.unmount()

    renderAt('/w/gone')

    expect(inside()).toBeNull()
    expect(screen.queryByText(/gone/)).not.toBeNull()
  })

  it('shows the no-workspace screen when the account belongs to none', () => {
    // A real state: an invitation can be revoked, and a platform administrator
    // can hold a role without joining anything.
    signedIn([], null)
    renderAt('/w/acme')

    expect(inside()).toBeNull()
    expect(screen.queryByText(/not in a workspace/i)).not.toBeNull()
  })

  it('matches on the slug rather than the name', () => {
    signedIn([ACME], 'ws-acme')
    renderAt('/w/Acme%20Platform')

    expect(inside()).toBeNull()
  })
})
