import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, setUnauthorizedHandler } from './apiClient'
import { fetchCurrentUser, login, logout } from './authApi'

function clearCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}
const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status })

async function rejection(p: Promise<unknown>): Promise<ApiError> {
  try {
    await p
  } catch (e) {
    return e as ApiError
  }
  throw new Error('expected rejection')
}

describe('authApi', () => {
  beforeEach(() => {
    clearCookie()
    document.cookie = 'XSRF-TOKEN=tok; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(null)
    clearCookie()
  })

  it('fetchCurrentUser returns the user on 200', async () => {
    const fetchMock = vi.fn(async (_path: string) => json(200, { username: 'bob', role: 'ADMIN' }))
    vi.stubGlobal('fetch', fetchMock)
    expect(await fetchCurrentUser()).toEqual({ username: 'bob', role: 'ADMIN' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/me')
  })

  it('fetchCurrentUser returns null on 401 without calling the handler', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { error: 'Authentication required' })))
    expect(await fetchCurrentUser()).toBeNull()
    expect(handler).not.toHaveBeenCalled()
  })

  it('login posts credentials and returns the user without triggering the handler', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    const fetchMock = vi.fn(async () => json(200, { username: 'u', role: 'USER' }))
    vi.stubGlobal('fetch', fetchMock)
    expect(await login('u', 'pw')).toEqual({ username: 'u', role: 'USER' })
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/auth/login')
    expect(init.method).toBe('POST')
    expect(init.body).toBe('{"username":"u","password":"pw"}')
  })

  it('login rejects with ApiError(401) on wrong password, handler not called', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { error: 'Invalid username or password' })))
    const err = await rejection(login('u', 'bad'))
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(401)
    expect(handler).not.toHaveBeenCalled()
  })

  it('logout posts to /api/auth/logout', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    await logout()
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/auth/logout')
    expect(init.method).toBe('POST')
  })

  it('logout treats 401 as success without firing the unauthorized handler', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { error: 'Authentication required' })))
    await expect(logout()).resolves.toBeUndefined()
    expect(handler).not.toHaveBeenCalled()
    setUnauthorizedHandler(null)
  })

  it('logout still rejects on other errors', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(500, { error: 'boom' })))
    const e = await rejection(logout())
    expect(e.status).toBe(500)
  })
})
