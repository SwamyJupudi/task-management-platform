import { ApiError, NetworkError } from './error'

import { api, configureApiClient } from './client'

/**
 * The API client: how a request is built, and what happens when one comes back
 * 401.
 *
 * This is the highest-consequence module in the application. Every feature goes
 * through it, the retry-on-401 decides whether somebody stays signed in, and
 * the single-flight refresh is what stops a page of expiring queries from
 * presenting a rotating token several times over — which the backend treats as
 * theft and answers by ending every session.
 *
 * `fetch` is replaced with a stub rather than mocked at the network layer. The
 * client is one funnel with one call in it, so a stub says everything a
 * request-interception library would and adds no dependency.
 */

/** A JSON response of the shape the backend actually sends. */
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** The backend's error envelope, so the parsing is tested against the real shape. */
function errorBody(status: number, code: string, message: string, errors?: unknown[]) {
  return {
    timestamp: '2026-09-13T00:00:00Z',
    status,
    code,
    message,
    path: '/api/v1/thing',
    requestId: 'req-1234',
    ...(errors ? { errors } : {}),
  }
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  // A clean client for every test: the module holds the token provider, the
  // refresher and the expiry handler in module state.
  configureApiClient({
    getToken: () => null,
    refreshToken: () => Promise.resolve(false),
    onSessionExpired: () => undefined,
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('buildUrl', () => {
  it('appends the base path and leaves a path without a leading slash alone', async () => {
    // A fresh Response per call: a body can only be read once, and both of
    // these get parsed.
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, {})))

    await api.get('/thing')
    await api.get('thing')

    const [first] = fetchMock.mock.calls[0] as [string]
    const [second] = fetchMock.mock.calls[1] as [string]
    expect(first).toBe(second)
    expect(first).toContain('/thing')
  })

  it('drops null and undefined parameters rather than sending them empty', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing', { params: { kept: 'yes', nothing: undefined, empty: null } })

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toContain('kept=yes')
    expect(url).not.toContain('nothing')
    expect(url).not.toContain('empty')
  })

  it('repeats an array parameter, which is what Spring binds to a List', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing', { params: { status: ['TODO', 'DONE'] } })

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toContain('status=TODO')
    expect(url).toContain('status=DONE')
    // Not comma-joined: a single `status=TODO,DONE` would bind as one value.
    expect(url).not.toContain('TODO%2CDONE')
  })

  it('skips null entries inside an array', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing', { params: { status: ['TODO', null, undefined, 'DONE'] } })

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url.match(/status=/g)).toHaveLength(2)
  })

  it('sends no query string at all when every parameter was dropped', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing', { params: { nothing: undefined } })

    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).not.toContain('?')
  })
})

describe('request headers', () => {
  it('carries the bearer token on an ordinary call', async () => {
    configureApiClient({ getToken: () => 'token-abc' })
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer token-abc')
  })

  it('omits the bearer token on an anonymous call even when one exists', async () => {
    configureApiClient({ getToken: () => 'token-abc' })
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing', { anonymous: true })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).get('Authorization')).toBeNull()
  })

  it('always sends credentials, which is how the refresh cookie travels', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.get('/thing')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.credentials).toBe('include')
  })

  it('serialises a JSON body and sets the content type', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await api.post('/thing', { name: 'Ada' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.body).toBe('{"name":"Ada"}')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('leaves FormData alone so the browser can set the multipart boundary', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))
    const form = new FormData()
    form.append('file', new Blob(['x']), 'x.txt')

    await api.post('/thing', form)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.body).toBe(form)
    expect(new Headers(init.headers).get('Content-Type')).toBeNull()
  })
})

describe('parsing a response', () => {
  it('returns undefined for 204 rather than trying to parse nothing', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(api.delete('/thing')).resolves.toBeUndefined()
  })

  it('parses a JSON body', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: '1', name: 'Ada' }))

    await expect(api.get('/thing')).resolves.toEqual({ id: '1', name: 'Ada' })
  })
})

describe('turning a failure into an ApiError', () => {
  it('reads the backend envelope, including the request id', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, errorBody(409, 'CONFLICT', 'This workspace is already archived.')),
    )

    const error = await api.get('/thing').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(409)
    expect(apiError.code).toBe('CONFLICT')
    expect(apiError.message).toBe('This workspace is already archived.')
    expect(apiError.requestId).toBe('req-1234')
  })

  it('exposes field violations so a form can put them back on its fields', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        400,
        errorBody(400, 'VALIDATION_ERROR', 'Some of the submitted values are not valid.', [
          { field: 'slug', message: 'must be lowercase words separated by single hyphens' },
        ]),
      ),
    )

    const error = (await api.post('/thing', {}).catch((caught: unknown) => caught)) as ApiError

    expect(error.isValidation).toBe(true)
    expect(error.fieldErrors()).toEqual({
      slug: 'must be lowercase words separated by single hyphens',
    })
  })

  it('falls back to a safe message when the body is not the envelope', async () => {
    fetchMock.mockResolvedValue(new Response('<html>gateway error</html>', { status: 502 }))

    const error = (await api.get('/thing').catch((caught: unknown) => caught)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(502)
    // Nothing from the HTML reaches the user.
    expect(error.message).not.toContain('html')
  })

  it('reports a transport failure as a NetworkError rather than an ApiError', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const error = await api.get('/thing').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(NetworkError)
  })
})

describe('retrying once behind a session renewal', () => {
  it('renews and repeats the request when a call carrying a token gets 401', async () => {
    const refreshToken = vi.fn().mockResolvedValue(true)
    configureApiClient({ getToken: () => 'stale', refreshToken })

    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, errorBody(401, 'UNAUTHORIZED', 'Sign in.')))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

    await expect(api.get('/thing')).resolves.toEqual({ ok: true })

    expect(refreshToken).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('gives up and reports the session expired when the renewal fails', async () => {
    const onSessionExpired = vi.fn()
    configureApiClient({
      getToken: () => 'stale',
      refreshToken: () => Promise.resolve(false),
      onSessionExpired,
    })

    fetchMock.mockResolvedValue(jsonResponse(401, errorBody(401, 'UNAUTHORIZED', 'Sign in.')))

    await expect(api.get('/thing')).rejects.toBeInstanceOf(ApiError)

    expect(onSessionExpired).toHaveBeenCalledTimes(1)
    // One attempt only: nothing was renewed, so nothing was repeated.
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('does not retry a second time when the repeat is also refused', async () => {
    configureApiClient({ getToken: () => 'stale', refreshToken: () => Promise.resolve(true) })

    fetchMock.mockImplementation(() =>
      Promise.resolve(jsonResponse(401, errorBody(401, 'UNAUTHORIZED', 'Sign in.'))),
    )

    await expect(api.get('/thing')).rejects.toBeInstanceOf(ApiError)

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('never renews for an anonymous call, because a 401 there is an answer', async () => {
    const refreshToken = vi.fn().mockResolvedValue(true)
    const onSessionExpired = vi.fn()
    configureApiClient({ getToken: () => 'live', refreshToken, onSessionExpired })

    fetchMock.mockResolvedValue(
      jsonResponse(401, errorBody(401, 'TOKEN_INVALID', 'That token is not valid.')),
    )

    await expect(api.get('/invitations', { anonymous: true })).rejects.toBeInstanceOf(ApiError)

    // This is the property the invitation screen depends on: a bad token must
    // not sign the reader out of a session they legitimately hold.
    expect(refreshToken).not.toHaveBeenCalled()
    expect(onSessionExpired).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('renews once for several requests that expire together', async () => {
    let resolveRefresh: ((renewed: boolean) => void) | undefined
    const refreshToken = vi.fn(
      () =>
        new Promise<boolean>((resolve) => {
          resolveRefresh = resolve
        }),
    )
    configureApiClient({ getToken: () => 'stale', refreshToken })

    // Every first attempt is refused; every repeat succeeds.
    let attempt = 0
    fetchMock.mockImplementation(() => {
      attempt += 1
      return Promise.resolve(
        attempt <= 3
          ? jsonResponse(401, errorBody(401, 'UNAUTHORIZED', 'Sign in.'))
          : jsonResponse(200, { ok: true }),
      )
    })

    const inFlight = Promise.all([api.get('/a'), api.get('/b'), api.get('/c')])
    // Let all three reach the refresh before it settles.
    await vi.waitFor(() => expect(refreshToken).toHaveBeenCalled())
    resolveRefresh?.(true)

    await expect(inFlight).resolves.toEqual([{ ok: true }, { ok: true }, { ok: true }])

    // Three presentations of a rotating token would be read as theft.
    expect(refreshToken).toHaveBeenCalledTimes(1)
  })
})

describe('api.raw', () => {
  it('hands back the response itself rather than parsing it', async () => {
    const response = new Response('bytes', { status: 200 })
    fetchMock.mockResolvedValue(response)

    await expect(api.raw('/attachments/1/content')).resolves.toBe(response)
  })

  it('still throws an ApiError on a failure, so nothing bypasses the funnel', async () => {
    fetchMock.mockResolvedValue(jsonResponse(404, errorBody(404, 'NOT_FOUND', 'Gone.')))

    await expect(api.raw('/attachments/1/content')).rejects.toBeInstanceOf(ApiError)
  })
})
