import { ApiError, apiFetch } from './apiClient'

export type Role = 'ADMIN' | 'USER'

export interface AuthUser {
  username: string
  role: Role
}

export async function fetchCurrentUser(): Promise<AuthUser | null> {
  try {
    return await apiFetch<AuthUser>('/api/auth/me', { handleUnauthorized: false })
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function login(username: string, password: string): Promise<AuthUser> {
  return apiFetch<AuthUser>('/api/auth/login', {
    method: 'POST',
    body: { username, password },
    handleUnauthorized: false,
  })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' })
}
