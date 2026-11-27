import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResearchOverviewPage } from './ResearchOverviewPage'
import type { AuthUser } from '../api/authApi'
import type { SnapshotSummary } from '../api/researchApi'

const user: AuthUser = { username: 'guest', role: 'USER' }

const snapshots: SnapshotSummary[] = [
  { ticker: 'AAPL', isin: 'US0378331005', companyName: 'Apple Inc.', country: 'US', group: { key: 'tech', name: 'Technologie', source: 'AI' }, lastResearchedAt: '2025-01-01', stale: true },
  { ticker: 'SAP', isin: 'DE0007164600', companyName: 'SAP SE', country: 'DE', group: { key: 'tech', name: 'Technologie', source: 'MANUAL' }, lastResearchedAt: '2026-10-01', stale: false },
  { ticker: 'XOM', isin: 'US30231G1022', companyName: 'Exxon Mobil', country: 'US', group: null, lastResearchedAt: '2026-10-01', stale: false },
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
    expect(screen.getByRole('heading', { name: 'No group' })).toBeInTheDocument()
  })

  it('marks stale snapshots as Stale', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    expect(screen.getAllByText('Stale')).toHaveLength(1)
    expect(within(screen.getByText('Apple Inc.').closest('li')!).getByText('Stale')).toHaveClass('badge', 'badge--stale')
  })

  it('switches to country sections', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    fireEvent.click(screen.getByLabelText('by country'))
    expect(screen.getByRole('heading', { name: 'DE' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'US' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Technologie' })).not.toBeInTheDocument()
  })

  it('search narrows the rows', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    await screen.findByText('Apple Inc.')
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'sap' } })
    expect(screen.getByText('SAP SE')).toBeInTheDocument()
    expect(screen.queryByText('Apple Inc.')).not.toBeInTheDocument()
  })

  it('marks held positions with In portfolio', async () => {
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    const row = (await screen.findByText('Apple Inc.')).closest('button')!
    expect(within(row).getByText('In portfolio')).toHaveClass('badge', 'badge--portfolio')
    const other = screen.getByText('SAP SE').closest('button')!
    expect(within(other).queryByText('In portfolio')).not.toBeInTheDocument()
  })

  it('still shows the overview without markers when the portfolio fails', async () => {
    mockBackend(() => res(500, { error: 'boom' }))
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByText('Apple Inc.')).toBeInTheDocument()
    expect(screen.queryByText('In portfolio')).not.toBeInTheDocument()
  })

  it('shows an error inline when snapshots fail', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(res(403, { error: 'Forbidden' }))))
    render(<ResearchOverviewPage user={user} navigate={vi.fn()} />)
    expect(await screen.findByText('Not allowed')).toBeInTheDocument()
  })

  it('clicking a row navigates to the detail view', async () => {
    const navigate = vi.fn()
    render(<ResearchOverviewPage user={user} navigate={navigate} />)
    fireEvent.click((await screen.findByText('SAP SE')).closest('button')!)
    expect(navigate).toHaveBeenCalledWith({ name: 'researchDetail', isin: 'DE0007164600' })
  })
})
