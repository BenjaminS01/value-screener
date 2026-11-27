import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortfolioPage } from './PortfolioPage'
import type { AuthUser } from '../api/authApi'

const admin: AuthUser = { username: 'ben', role: 'ADMIN' }
const user: AuthUser = { username: 'guest', role: 'USER' }

const userRows = [
  { ticker: 'AAPL', isin: 'US0378331005', companyName: 'Apple Inc.', group: { key: 'tech', name: 'Technologie' }, quantity: 5 },
  { ticker: 'MSFT', isin: 'US5949181045', companyName: 'Microsoft Corp.', group: null, quantity: 7 },
]
const adminRows = [
  { ticker: 'AAPL', isin: 'US0378331005', companyName: 'Apple Inc.', group: { key: 'tech', name: 'Technologie' }, quantity: 1234, entryPrice: 1234.5, purchaseDate: '2026-01-15' },
  { ticker: 'MSFT', isin: 'US5949181045', companyName: 'Microsoft Corp.', group: null, quantity: 3, entryPrice: 300, purchaseDate: '2025-12-01' },
]

function res(status: number, body?: unknown) {
  return { ok: status < 400, status, headers: new Headers(), json: async () => body , text: async () => JSON.stringify(body )}
}

function mockBackend(rows: unknown[], write?: (url: string, method: string) => ReturnType<typeof res>) {
  const mock = vi.fn((url: string, options?: { method?: string }) => {
    const method = options?.method ?? 'GET'
    if (url === '/api/portfolio' && method === 'GET') return Promise.resolve(res(200, rows))
    if (url === '/api/research/snapshots') return Promise.resolve(res(200, [{ isin: 'US0378331005' }]))
    if (write) return Promise.resolve(write(url, method))
    return Promise.resolve(res(404, {}))
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('PortfolioPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok'
  })

  it('USER sees only company, ticker and group; no quantity or action buttons', async () => {
    mockBackend(userRows)
    render(<PortfolioPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Portfolio' })).toBeInTheDocument()
    for (const name of ['Company', 'Ticker', 'Group']) {
      expect(screen.getByRole('columnheader', { name })).toBeInTheDocument()
    }
    for (const name of ['Quantity', 'Entry price', 'Purchase date']) {
      expect(screen.queryByText(name)).not.toBeInTheDocument()
    }
    for (const name of ['Buy', 'Partial sale', 'Correct', 'Delete']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
    }
    expect(screen.queryByText('5')).not.toBeInTheDocument()
    expect(screen.queryByText('7')).not.toBeInTheDocument()
  })

  it('shows a dash for a missing group', async () => {
    mockBackend(userRows)
    render(<PortfolioPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Microsoft Corp.')
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('Technologie')).toBeInTheDocument()
  })

  it('ADMIN sees extra columns with en-US values and action buttons', async () => {
    mockBackend(adminRows)
    render(<PortfolioPage user={admin} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    for (const name of ['Quantity', 'Entry price', 'Purchase date']) {
      expect(screen.getByRole('columnheader', { name })).toBeInTheDocument()
    }
    expect(screen.getByText('1,234')).toBeInTheDocument()
    expect(screen.getByText('1,234.50')).toBeInTheDocument()
    expect(screen.getByText('15 Jan 2026')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Partial sale' })).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: 'Correct' })).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: 'Delete' })).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Buy' })).toBeInTheDocument()
  })

  it('separates held positions and the buy form with headings; USER sees no buy heading', async () => {
    mockBackend(adminRows)
    const { unmount } = render(<PortfolioPage user={admin} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    expect(screen.getByRole('heading', { level: 2, name: 'Holdings' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Buy position' })).toBeInTheDocument()
    unmount()
    mockBackend(userRows)
    render(<PortfolioPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    expect(screen.getByRole('heading', { level: 2, name: 'Holdings' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Buy position' })).not.toBeInTheDocument()
  })

  it('click on a researched position navigates to the detail', async () => {
    mockBackend(userRows)
    const navigate = vi.fn()
    render(<PortfolioPage user={user} navigate={navigate} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Apple Inc.' }))
    await waitFor(() => expect(navigate).toHaveBeenCalledWith({ name: 'researchDetail', isin: 'US0378331005' }))
  })

  it('click on an unresearched position shows a note and does not navigate', async () => {
    mockBackend(userRows)
    const navigate = vi.fn()
    render(<PortfolioPage user={user} navigate={navigate} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Microsoft Corp.' }))
    expect(await screen.findByText('Not researched yet')).toBeInTheDocument()
    expect(navigate).not.toHaveBeenCalled()
  })

  it('ADMIN delete asks for confirmation and reloads the list after 204', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const mock = mockBackend(adminRows, (_url, method) => (method === 'DELETE' ? res(204) : res(404, {})))
    render(<PortfolioPage user={admin} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    const row = screen.getByText('Apple Inc.').closest('tr')!
    fireEvent.click(within(row).getByRole('button', { name: 'Delete' }))
    expect(window.confirm).toHaveBeenCalled()
    await waitFor(() => {
      const gets = mock.mock.calls.filter(([u, o]) => u === '/api/portfolio' && (o?.method ?? 'GET') === 'GET')
      expect(gets).toHaveLength(2)
    })
    expect(mock).toHaveBeenCalledWith('/api/portfolio/US0378331005', expect.objectContaining({ method: 'DELETE' }))
  })

  it('on 409 shows the conflict text, reloads the list and keeps the text', async () => {
    const mock = mockBackend(adminRows, (_u, method) => (method === 'PUT' ? res(409, { error: 'conflict' }) : res(404, {})))
    render(<PortfolioPage user={admin} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    const row = screen.getByText('Apple Inc.').closest('tr')!
    fireEvent.click(within(row).getByRole('button', { name: 'Correct' }))
    fireEvent.change(within(row).getByLabelText('Quantity'), { target: { value: '1' } })
    fireEvent.click(within(row).getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('The position was changed in the meantime, please reload')).toBeInTheDocument()
    await waitFor(() => {
      const gets = mock.mock.calls.filter(([u, o]) => u === '/api/portfolio' && (o?.method ?? 'GET') === 'GET')
      expect(gets).toHaveLength(2)
    })
    expect(screen.getByText('The position was changed in the meantime, please reload')).toBeInTheDocument()
  })

  it('shows Not allowed on a 403 write', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockBackend(adminRows, (_u, method) => (method === 'DELETE' ? res(403, { error: 'Forbidden' }) : res(404, {})))
    render(<PortfolioPage user={admin} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    const row = screen.getByText('Apple Inc.').closest('tr')!
    fireEvent.click(within(row).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('Not allowed')).toBeInTheDocument()
  })

  it('shows an error when loading fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res(500, { error: 'boom' })))
    render(<PortfolioPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
  })

  it('shows a reload error inline and keeps heading, table and buy form', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let getCount = 0
    const mock = vi.fn((url: string, options?: { method?: string }) => {
      const method = options?.method ?? 'GET'
      if (url === '/api/portfolio' && method === 'GET') {
        getCount += 1
        return Promise.resolve(getCount === 1 ? res(200, adminRows) : res(403, { error: 'Forbidden' }))
      }
      if (url === '/api/research/snapshots') return Promise.resolve(res(200, []))
      if (method === 'DELETE') return Promise.resolve(res(204))
      return Promise.resolve(res(404, {}))
    })
    vi.stubGlobal('fetch', mock)
    render(<PortfolioPage user={admin} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    const row = screen.getByText('Apple Inc.').closest('tr')!
    fireEvent.click(within(row).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Not allowed')
    expect(screen.getByRole('heading', { name: 'Portfolio' })).toBeInTheDocument()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Buy' })).toBeInTheDocument()
  })
})
