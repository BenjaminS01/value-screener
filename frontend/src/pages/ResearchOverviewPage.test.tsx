import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResearchOverviewPage } from './ResearchOverviewPage'
import type { AuthUser } from '../api/authApi'

const user: AuthUser = { username: 'guest', role: 'USER' }

const snapshots = [
  { id: 1, ticker: 'AAPL', isin: 'US0378331005', companyName: 'Apple Inc.', country: 'US', businessDescription: '', group: { key: 'tech', name: 'Technologie', source: 'AI' }, findings: [] },
  { id: 2, ticker: 'SAP', isin: 'DE0007164600', companyName: 'SAP SE', country: 'DE', businessDescription: '', group: { key: 'tech', name: 'Technologie', source: 'MANUAL' }, findings: [] },
  { id: 3, ticker: 'XOM', isin: 'US30231G1022', companyName: 'Exxon Mobil', country: 'US', businessDescription: '', group: null, findings: [] },
]
const groups = [{ key: 'tech', name: 'Technologie', sortOrder: 1, stockCount: 2 }]

function res(status: number, body?: unknown) {
  return { ok: status < 400, status, headers: new Headers(), json: async () => body , text: async () => JSON.stringify(body )}
}

function mockBackend(portfolio: () => ReturnType<typeof res> = () => res(200, [{ isin: 'US0378331005' }])) {
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    if (url === '/api/research/snapshots') return Promise.resolve(res(200, snapshots))
    if (url === '/api/research/groups') return Promise.resolve(res(200, groups))
    if (url === '/api/portfolio') return Promise.resolve(portfolio())
    return Promise.resolve(res(404, {}))
  }))
}

describe('ResearchOverviewPage', () => {
  beforeEach(() => mockBackend())

  it('groups by stock group by default', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByRole('heading', { name: 'Technologie' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Research' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ohne Gruppe' })).toBeInTheDocument()
  })

  it('switches to country sections', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    fireEvent.click(screen.getByLabelText('nach Land'))
    expect(screen.getByRole('heading', { name: 'DE' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'US' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Technologie' })).not.toBeInTheDocument()
  })

  it('search narrows the rows', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    fireEvent.change(screen.getByLabelText('Suche'), { target: { value: 'sap' } })
    expect(screen.getByText('SAP SE')).toBeInTheDocument()
    expect(screen.queryByText('Apple Inc.')).not.toBeInTheDocument()
  })

  it('marks held positions with im Portfolio', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    const row = (await screen.findByText('Apple Inc.')).closest('button')!
    expect(within(row).getByText('im Portfolio')).toBeInTheDocument()
    const other = screen.getByText('SAP SE').closest('button')!
    expect(within(other).queryByText('im Portfolio')).not.toBeInTheDocument()
  })

  it('still shows the overview without markers when the portfolio fails', async () => {
    mockBackend(() => res(500, { error: 'boom' }))
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.queryByText('im Portfolio')).not.toBeInTheDocument()
  })

  it('shows an error inline when snapshots fail', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(res(403, { error: 'Forbidden' }))))
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByText('Keine Berechtigung')).toBeInTheDocument()
  })

  it('clicking a row navigates to the detail view', async () => {
    const navigate = vi.fn()
    render(<ResearchOverviewPage user={user} navigate={navigate} />)
    fireEvent.click((await screen.findByText('SAP SE')).closest('button')!)
    expect(navigate).toHaveBeenCalledWith({ name: 'researchDetail', isin: 'DE0007164600' })
  })
})
