import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

function res(status: number, body?: unknown, headers: Record<string, string> = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    json: async () => body, text: async () => JSON.stringify(body),
  }
}

function fill(username: string, password: string) {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok'
  })

  it('calls onLoggedIn with the returned user', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res(200, { username: 'ben', role: 'ADMIN' })))
    const onLoggedIn = vi.fn()
    render(<LoginPage onLoggedIn={onLoggedIn} navigate={vi.fn()} />)
    fill('ben', 'pw')
    await waitFor(() => expect(onLoggedIn).toHaveBeenCalledWith({ username: 'ben', role: 'ADMIN' }))
  })

  it('shows an error on 401, empties the password and keeps the username', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res(401, { error: 'Invalid username or password' })))
    render(<LoginPage onLoggedIn={vi.fn()} navigate={vi.fn()} />)
    fill('ben', 'pw')
    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByLabelText('Username')).toHaveValue('ben')
  })

  it('shows minutes from Retry-After on 429', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(res(429, { error: 'Too many login attempts' }, { 'Retry-After': '61' })),
    )
    render(<LoginPage onLoggedIn={vi.fn()} navigate={vi.fn()} />)
    fill('ben', 'pw')
    expect(
      await screen.findByText('Too many login attempts, please try again in 2 minutes'),
    ).toBeInTheDocument()
  })

  it('shows a generic 429 message without Retry-After', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res(429, { error: 'Too many login attempts' })))
    render(<LoginPage onLoggedIn={vi.fn()} navigate={vi.fn()} />)
    fill('ben', 'pw')
    expect(
      await screen.findByText('Too many login attempts, please try again later'),
    ).toBeInTheDocument()
  })

  it('shows a generic error for other failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res(500, { error: 'boom' })))
    render(<LoginPage onLoggedIn={vi.fn()} navigate={vi.fn()} />)
    fill('ben', 'pw')
    expect(await screen.findByText('Login failed')).toBeInTheDocument()
  })

  it('disables the button while the request is pending', async () => {
    let resolve: (v: unknown) => void = () => {}
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise((r) => (resolve = r))))
    render(<LoginPage onLoggedIn={vi.fn()} navigate={vi.fn()} />)
    fill('ben', 'pw')
    expect(screen.getByRole('button', { name: 'Log in' })).toBeDisabled()
    resolve(res(401, { error: 'x' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled())
  })

  it('shows the notice prop', () => {
    render(<LoginPage onLoggedIn={vi.fn()} navigate={vi.fn()} notice="Hinweis XY" />)
    expect(screen.getByText('Hinweis XY')).toBeInTheDocument()
  })

  it('has Impressum and Datenschutz links', () => {
    const navigate = vi.fn()
    render(<LoginPage onLoggedIn={vi.fn()} navigate={navigate} />)
    fireEvent.click(screen.getByRole('button', { name: 'Legal notice (Impressum)' }))
    expect(navigate).toHaveBeenCalledWith({ name: 'impressum' })
    fireEvent.click(screen.getByRole('button', { name: 'Privacy (Datenschutz)' }))
    expect(navigate).toHaveBeenCalledWith({ name: 'datenschutz' })
  })
})
