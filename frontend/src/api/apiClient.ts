export class ApiError extends Error {
  status: number
  retryAfterSeconds: number | null

  constructor(message: string, status: number, retryAfterSeconds: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.retryAfterSeconds = retryAfterSeconds
  }
}

interface ApiFetchOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  handleUnauthorized?: boolean
}

let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

function readXsrfCookie(): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  if (!match) return null
  const value = match.substring('XSRF-TOKEN='.length)
  return value ? decodeURIComponent(value) : null
}

async function ensureXsrfToken(): Promise<string | null> {
  const existing = readXsrfCookie()
  if (existing) return existing
  await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  return readXsrfCookie()
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const data = await response.json()
    if (data && typeof data.error === 'string' && data.error) return data.error
  } catch {
    // non-JSON or empty body
  }
  return `HTTP ${response.status}`
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = await ensureXsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (!response.ok) {
    const retryAfter = response.headers.get('Retry-After')
    const parsed = retryAfter !== null ? Number(retryAfter) : NaN
    const error = new ApiError(
      await errorMessage(response),
      response.status,
      Number.isFinite(parsed) ? parsed : null,
    )
    if (response.status === 401 && options.handleUnauthorized !== false) {
      unauthorizedHandler?.()
    }
    throw error
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
