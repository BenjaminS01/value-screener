import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { apiFetch } from './api/apiClient'

vi.mock('./pages/PortfolioPage', async () => {
  const React = await import('react')
  return {
    PortfolioPage: () => {
      React.useEffect(() => {
        apiFetch('/api/some-page-call').catch(() => {})
      }, [])
      return <h1>Portfolio-Seite</h1>
    },
  }
})
vi.mock('./pages/ResearchOverviewPage', () => ({
  ResearchOverviewPage: () => <h1>Research-Seite</h1>,
}))

const USER = { username: 'ben', role: 'ADMIN' }

function res(status: number, body?: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: async () => body, text: async () => JSON.stringify(body),
  }
}

function stubFetch(routes: Record<string, () => unknown>) {
  const fn = vi.fn(async (path: string, init?: { method?: string }) => {
    const key = `${init?.method ?? 'GET'} ${path}`
    const handler = routes[key]
    if (!handler) throw new Error(`unexpected fetch ${key}`)
    return handler()
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

describe('App', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok'
  })

  it('shows the login page when /api/auth/me returns 401', async () => {
    stubFetch({ 'GET /api/auth/me': () => res(401, { error: 'Authentication required' }) })
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Portfolio' })).not.toBeInTheDocument()
  })

  it('shows header and start page when logged in', async () => {
    stubFetch({ 'GET /api/auth/me': () => res(200, USER) })
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Unsere Anwendung' })).toBeInTheDocument()
    expect(screen.getByText('ben (ADMIN)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Portfolio' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Research' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abmelden' })).toBeInTheDocument()
  })

  it('logout returns to the login page', async () => {
    const fetchMock = stubFetch({
      'GET /api/auth/me': () => res(200, USER),
      'POST /api/auth/logout': () => res(204),
    })
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: 'Abmelden' }))
    expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(screen.queryByText('Sitzung abgelaufen, bitte erneut anmelden')).not.toBeInTheDocument()
  })

  it('logout after the session expired ends cleanly on the login page', async () => {
    const fetchMock = stubFetch({
      'GET /api/auth/me': () => res(200, USER),
      'POST /api/auth/logout': () => res(401, { error: 'Authentication required' }),
    })
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: 'Abmelden' }))
    expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([p]) => p === '/api/auth/me')).toHaveLength(1)
    expect(screen.queryByText('Sitzung abgelaufen, bitte erneut anmelden')).not.toBeInTheDocument()
  })

  it('Impressum and Datenschutz are reachable without login', async () => {
    stubFetch({ 'GET /api/auth/me': () => res(401, { error: 'Authentication required' }) })
    render(<App />)
    await screen.findByRole('heading', { name: 'Anmelden' })
    fireEvent.click(screen.getAllByRole('button', { name: 'Impressum' })[0])
    expect(screen.getByRole('heading', { name: 'Impressum' })).toBeInTheDocument()
    fireEvent.click(screen.getAllByRole('button', { name: 'Datenschutz' })[0])
    expect(screen.getByRole('heading', { name: 'Datenschutz' })).toBeInTheDocument()
  })

  it('a 401 from any later call returns to login with the session note', async () => {
    const fetchMock = stubFetch({
      'GET /api/auth/me': () => res(200, USER),
      'GET /api/some-page-call': () => res(401, { error: 'Authentication required' }),
    })
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: 'Portfolio' }))
    expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument()
    expect(screen.getByText('Sitzung abgelaufen, bitte erneut anmelden')).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchMock.mock.calls.filter(([p]) => p === '/api/auth/me')).toHaveLength(1)
    })
  })
})
