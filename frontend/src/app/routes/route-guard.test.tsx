// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

import { useSessionStore } from '@/stores/session-store'

import { RequirePermission } from './route-guard'

/**
 * The permission guard, and the distinction the admin panel depends on.
 *
 * `RequirePermission` answers on the union of platform and workspace grants by
 * default, and on platform grants alone when `platform` is set. That difference
 * is not cosmetic: several codes the admin panel uses — `user:read`,
 * `role:read`, `role:manage`, `permission:read`, `activity:read` — are also held
 * by the seeded workspace administrator, so a guard asking the wrong question
 * would let a workspace administrator into a panel the API answers 403 to.
 *
 * That was a real bug caught during the admin phase. These tests are what stop
 * it coming back.
 */

function signedIn(platformPermissions: string[], workspacePermissions: string[]) {
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
    platformPermissions,
    memberships: [],
    activeWorkspaceId: 'ws-1',
    workspacePermissions,
    workspacePermissionsLoaded: true,
    accessToken: 'token',
  })
}

function renderGuard(element: React.ReactElement) {
  return render(
    <MemoryRouter initialEntries={['/guarded']}>
      <Routes>
        <Route element={element}>
          <Route path="/guarded" element={<p>behind the guard</p>} />
        </Route>
        <Route path="/403" element={<p>forbidden</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

const behind = () => screen.queryByText('behind the guard')
const refused = () => screen.queryByText('forbidden')

beforeEach(() => {
  useSessionStore.getState().clear()
})

describe('RequirePermission, the ordinary union check', () => {
  it('admits somebody holding the code in the workspace', () => {
    signedIn([], ['task:read'])
    renderGuard(<RequirePermission codes={['task:read']} />)

    expect(behind()).not.toBeNull()
  })

  it('admits somebody holding it platform-wide', () => {
    signedIn(['task:read'], [])
    renderGuard(<RequirePermission codes={['task:read']} />)

    expect(behind()).not.toBeNull()
  })

  it('refuses somebody holding neither', () => {
    signedIn([], ['project:read'])
    renderGuard(<RequirePermission codes={['task:read']} />)

    expect(behind()).toBeNull()
    expect(refused()).not.toBeNull()
  })

  it('admits on any one code by default', () => {
    signedIn([], ['project:read_any'])
    renderGuard(<RequirePermission codes={['project:read', 'project:read_any']} />)

    expect(behind()).not.toBeNull()
  })

  it('insists on all of them when asked to', () => {
    signedIn([], ['project:read'])
    renderGuard(<RequirePermission codes={['project:read', 'task:read']} requireAll />)

    expect(behind()).toBeNull()

    useSessionStore.getState().clear()
    signedIn([], ['project:read', 'task:read'])
    renderGuard(<RequirePermission codes={['project:read', 'task:read']} requireAll />)

    expect(behind()).not.toBeNull()
  })
})

describe('RequirePermission, the platform-scoped check', () => {
  it('refuses a workspace grant, however wide', () => {
    // The seeded workspace administrator holds user:read inside their own
    // workspace and nothing at all on the platform.
    signedIn([], ['user:read', 'role:read', 'role:manage', 'permission:read', 'activity:read'])
    renderGuard(<RequirePermission codes={['user:read']} platform />)

    expect(behind()).toBeNull()
    expect(refused()).not.toBeNull()
  })

  it('admits a platform grant', () => {
    signedIn(['user:read'], [])
    renderGuard(<RequirePermission codes={['user:read']} platform />)

    expect(behind()).not.toBeNull()
  })

  it('insists on all of them platform-wide when asked to', () => {
    signedIn(['admin:read_system'], ['activity:read'])
    renderGuard(
      <RequirePermission codes={['admin:read_system', 'activity:read']} platform requireAll />,
    )

    // The workspace half does not count towards a platform requirement.
    expect(behind()).toBeNull()

    useSessionStore.getState().clear()
    signedIn(['admin:read_system', 'activity:read'], [])
    renderGuard(
      <RequirePermission codes={['admin:read_system', 'activity:read']} platform requireAll />,
    )

    expect(behind()).not.toBeNull()
  })
})

describe('RequirePermission while the permission set is unknown', () => {
  it('waits rather than refusing', () => {
    // An empty set is both "still loading" and "holds nothing"; bouncing a
    // legitimate member to the forbidden page for the moment before the request
    // returns is the bug `workspacePermissionsLoaded` exists to prevent.
    useSessionStore.setState({
      status: 'authenticated',
      activeWorkspaceId: 'ws-1',
      workspacePermissions: [],
      workspacePermissionsLoaded: false,
      platformPermissions: [],
    })

    renderGuard(<RequirePermission codes={['task:read']} />)

    expect(behind()).toBeNull()
    expect(refused()).toBeNull()
  })

  it('renders children rather than an outlet when it is given them', () => {
    signedIn([], ['task:read'])

    render(
      <MemoryRouter>
        <RequirePermission codes={['task:read']}>
          <p>as a child</p>
        </RequirePermission>
      </MemoryRouter>,
    )

    expect(screen.queryByText('as a child')).not.toBeNull()
  })
})
