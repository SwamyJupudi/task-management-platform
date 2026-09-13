import { useSessionStore } from '@/stores/session-store'

import * as authApi from './api'
import { endSession, establishSession, reloadCurrentUser } from './session-manager'
import type { AuthTokens } from './types'

/**
 * The session lifecycle, and the races it was written to survive.
 *
 * Almost every line of `session-manager.ts` exists because of a specific
 * ordering problem: a refresh that lands after sign-out, two refreshes
 * presenting one rotating token, a bootstrap running twice under StrictMode.
 * None of those is visible in ordinary use and all of them are serious, which
 * is exactly the shape of thing worth pinning down in tests.
 *
 * The module holds state between calls — an in-flight promise, a generation
 * counter, a memoised bootstrap — so each test resets it through `resetModules`
 * and re-imports. Sharing one instance across tests would make them pass or
 * fail depending on their order.
 */

const TOKENS: AuthTokens = {
  accessToken: 'access-1',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: 'user-1',
    email: 'ada@example.com',
    firstName: 'Ada',
    lastName: 'Lovelace',
    status: 'ACTIVE',
    emailVerified: true,
    lastLoginAt: null,
    createdAt: '2026-01-01T00:00:00Z',
  },
}

const CURRENT_USER = {
  user: TOKENS.user,
  platformRole: null,
  platformPermissions: [],
  memberships: [
    {
      workspaceId: 'ws-1',
      workspaceName: 'Acme',
      workspaceSlug: 'acme',
      roleSlug: 'ADMIN',
      roleName: 'Admin',
    },
  ],
}

/**
 * A fresh module graph, because this module keeps state between calls.
 *
 * The API module and the store come back too, and that is not optional:
 * `resetModules` gives the new manager new copies of its own imports, so a spy
 * on the old `./api` would watch a module nothing calls any more, and the old
 * store would never see the writes.
 */
async function freshGraph() {
  vi.resetModules()
  const api = await import('./api')
  const store = await import('@/stores/session-store')
  const manager = await import('./session-manager')
  store.useSessionStore.getState().clear()
  return { api, store: store.useSessionStore, manager }
}

beforeEach(() => {
  vi.useFakeTimers()
  useSessionStore.getState().clear()
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('establishSession', () => {
  it('records the token and loads the profile behind it', async () => {
    vi.spyOn(authApi, 'me').mockResolvedValue(CURRENT_USER)

    await establishSession(TOKENS)

    const state = useSessionStore.getState()
    expect(state.accessToken).toBe('access-1')
    expect(state.status).toBe('authenticated')
    expect(state.user?.email).toBe('ada@example.com')
    expect(state.memberships).toHaveLength(1)
  })

  it('schedules a renewal before the token actually expires', async () => {
    vi.spyOn(authApi, 'me').mockResolvedValue(CURRENT_USER)
    const refresh = vi.spyOn(authApi, 'refresh').mockResolvedValue(TOKENS)

    await establishSession(TOKENS)
    expect(refresh).not.toHaveBeenCalled()

    // 900s of life, renewed 60s early.
    await vi.advanceTimersByTimeAsync(840_000)
    expect(refresh).toHaveBeenCalledTimes(1)
  })
})

describe('refreshSession', () => {
  it('resolves false rather than throwing when there is no usable cookie', async () => {
    const { api, manager } = await freshGraph()
    vi.spyOn(api, 'refresh').mockRejectedValue(new Error('no cookie'))

    // A first visit is the ordinary case, not a failure.
    await expect(manager.refreshSession()).resolves.toBe(false)
  })

  it('runs one refresh for several concurrent callers', async () => {
    const { api, manager } = await freshGraph()
    let resolveRefresh: ((tokens: AuthTokens) => void) | undefined
    const refresh = vi.spyOn(api, 'refresh').mockReturnValue(
      new Promise<AuthTokens>((resolve) => {
        resolveRefresh = resolve
      }),
    )

    const all = Promise.all([
      manager.refreshSession(),
      manager.refreshSession(),
      manager.refreshSession(),
    ])
    resolveRefresh?.(TOKENS)

    await expect(all).resolves.toEqual([true, true, true])
    // Rotation means a second presentation of the same token reads as theft.
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('discards a refresh that lands after the session ended', async () => {
    const { api, store, manager } = await freshGraph()
    let resolveRefresh: ((tokens: AuthTokens) => void) | undefined
    vi.spyOn(api, 'refresh').mockReturnValue(
      new Promise<AuthTokens>((resolve) => {
        resolveRefresh = resolve
      }),
    )

    const inFlight = manager.refreshSession()
    // Somebody signs out while the refresh is still on the wire.
    manager.endSession()
    resolveRefresh?.(TOKENS)

    await expect(inFlight).resolves.toBe(false)
    // The token that came back is real, and nobody is waiting for it. Taking it
    // would re-arm a session the user asked to leave.
    expect(store.getState().accessToken).toBeNull()
    expect(store.getState().status).not.toBe('authenticated')
  })
})

describe('bootstrapSession', () => {
  it('marks the session anonymous when the cookie buys nothing', async () => {
    const { api, store, manager } = await freshGraph()
    vi.spyOn(api, 'refresh').mockRejectedValue(new Error('no cookie'))

    await manager.bootstrapSession()

    expect(store.getState().status).toBe('anonymous')
  })

  it('restores a session from the cookie alone', async () => {
    const { api, store, manager } = await freshGraph()
    vi.spyOn(api, 'refresh').mockResolvedValue(TOKENS)
    vi.spyOn(api, 'me').mockResolvedValue(CURRENT_USER)

    await manager.bootstrapSession()

    expect(store.getState().status).toBe('authenticated')
    expect(store.getState().user?.id).toBe('user-1')
  })

  it('spends the rotating cookie once however many times it is called', async () => {
    const { api, manager } = await freshGraph()
    const refresh = vi.spyOn(api, 'refresh').mockResolvedValue(TOKENS)
    vi.spyOn(api, 'me').mockResolvedValue(CURRENT_USER)

    // StrictMode mounts effects twice in development.
    await Promise.all([manager.bootstrapSession(), manager.bootstrapSession()])
    await manager.bootstrapSession()

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('ends the session when the token is good but the account cannot be described', async () => {
    const { api, store, manager } = await freshGraph()
    vi.spyOn(api, 'refresh').mockResolvedValue(TOKENS)
    vi.spyOn(api, 'me').mockRejectedValue(new Error('deactivated'))

    await manager.bootstrapSession()

    // Deactivated between sessions, or deleted. Anything but leaving the store
    // at `unknown`, which would hold every guard at a spinner for good.
    expect(store.getState().status).not.toBe('unknown')
    expect(store.getState().accessToken).toBeNull()
  })
})

describe('reloadCurrentUser', () => {
  it('re-reads the memberships, which is what joining a workspace changes', async () => {
    vi.spyOn(authApi, 'me').mockResolvedValueOnce(CURRENT_USER)
    await establishSession(TOKENS)
    expect(useSessionStore.getState().memberships).toHaveLength(1)

    vi.spyOn(authApi, 'me').mockResolvedValueOnce({
      ...CURRENT_USER,
      memberships: [
        ...CURRENT_USER.memberships,
        {
          workspaceId: 'ws-2',
          workspaceName: 'Side',
          workspaceSlug: 'side',
          roleSlug: 'EMPLOYEE',
          roleName: 'Employee',
        },
      ],
    })

    await reloadCurrentUser()

    expect(useSessionStore.getState().memberships).toHaveLength(2)
  })
})

describe('endSession', () => {
  it('empties the store and cancels the scheduled renewal', async () => {
    vi.spyOn(authApi, 'me').mockResolvedValue(CURRENT_USER)
    const refresh = vi.spyOn(authApi, 'refresh').mockResolvedValue(TOKENS)

    await establishSession(TOKENS)
    endSession()

    expect(useSessionStore.getState().accessToken).toBeNull()
    expect(useSessionStore.getState().user).toBeNull()

    // The renewal that was armed must not fire for a session that is over.
    await vi.advanceTimersByTimeAsync(900_000)
    expect(refresh).not.toHaveBeenCalled()
  })
})

describe('refreshSession, again', () => {
  it('is reusable after a failure rather than stuck on the first one', async () => {
    const { api, manager } = await freshGraph()
    const refresh = vi
      .spyOn(api, 'refresh')
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(TOKENS)

    await expect(manager.refreshSession()).resolves.toBe(false)
    await expect(manager.refreshSession()).resolves.toBe(true)

    expect(refresh).toHaveBeenCalledTimes(2)
  })
})
