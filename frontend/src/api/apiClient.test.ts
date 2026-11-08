import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, setUnauthorizedHandler } from './apiClient'

function clearCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

function jsonResponse(status: number, body?: unknown, headers?: Record<string, string>) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers })
}

async function rejection(p: Promise<unknown>): Promise<ApiError> {
  try {
    await p
  } catch (e) {
    return e as ApiError
  }
  throw new Error('expected rejection')
}

describe('apiFetch', () => {
  beforeEach(() => {
    clearCookie()
    setUnauthorizedHandler(null)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    clearCookie()
    setUnauthorizedHandler(null)
  })

  it('sends credentials same-origin and JSON body', async () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    const fetchMock = vi.fn(async (_p: string) => jsonResponse(200, {}))
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/api/x', { method: 'POST', body: { a: 1 } })
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/x')
    expect(init.credentials).toBe('same-origin')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
    expect(init.body).toBe('{"a":1}')
  })

  it('adds X-XSRF-TOKEN from the cookie on writes', async () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    const fetchMock = vi.fn(async (_p: string) => jsonResponse(200, {}))
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/api/x', { method: 'POST', body: {} })
    await apiFetch('/api/x')
    const post = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const get = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(new Headers(post[1].headers).get('X-XSRF-TOKEN')).toBe('abc')
    expect(new Headers(get[1].headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('fetches the csrf endpoint first when the cookie is missing', async () => {
    const fetchMock = vi.fn(async (path: string) => {
      if (path === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/'
        return jsonResponse(200, { headerName: 'X-XSRF-TOKEN', token: 'fresh' })
      }
      return jsonResponse(200, {})
    })
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/api/x', { method: 'POST', body: {} })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    const [path, init] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect(path).toBe('/api/x')
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('fresh')
  })

  it('throws ApiError with the backend error message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(409, { error: 'Conflict, please reload' })))
    const err = await rejection(apiFetch('/api/x'))
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(409)
    expect(err.message).toBe('Conflict, please reload')
  })

  it('falls back to HTTP status when the error body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('oops', { status: 500 })))
    const err = await rejection(apiFetch('/api/x'))
    expect(err.message).toBe('HTTP 500')
  })

  it('reads Retry-After', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(429, { error: 'x' }, { 'Retry-After': '61' })))
    const err = await rejection(apiFetch('/api/x'))
    expect(err.retryAfterSeconds).toBe(61)
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(429, { error: 'x' })))
    const err2 = await rejection(apiFetch('/api/x'))
    expect(err2.retryAfterSeconds).toBeNull()
  })

  it('calls the unauthorized handler on 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(401, { error: 'Authentication required' })))
    const err = await rejection(apiFetch('/api/x'))
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(401)
    expect(handler).toHaveBeenCalledTimes(1)
    await apiFetch('/api/x', { handleUnauthorized: false }).catch(() => {})
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('returns undefined for 204', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))
    expect(await apiFetch('/api/x')).toBeUndefined()
  })
})
