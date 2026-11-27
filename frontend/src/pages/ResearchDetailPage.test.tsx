import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResearchDetailPage } from './ResearchDetailPage'
import type { AuthUser } from '../api/authApi'

const admin: AuthUser = { username: 'ben', role: 'ADMIN' }
const guest: AuthUser = { username: 'guest', role: 'USER' }

function snapshot(over: Record<string, unknown> = {}) {
  return {
    ticker: 'AAPL', isin: 'US0378331005', companyName: 'Apple Inc.', country: 'US',
    businessDescription: 'Macht iPhones',
    group: { key: 'tech', name: 'Technologie', source: 'AI' },
    findings: [
      { criterionKey: 'PE_RATIO', numericValue: 12.5, booleanValue: null, claim: 'P/E is 12.5', sourceUrl: 'https://example.com/pe', asOfDate: '2026-03-31', stale: false },
      { criterionKey: 'CURRENT_YEAR_FCF_POSITIVE', numericValue: null, booleanValue: true, claim: 'FCF positiv', sourceUrl: 'https://example.com/fcf', asOfDate: '2026-03-31', stale: false },
      { criterionKey: 'CURRENT_YEAR_NET_INCOME_GREW', numericValue: null, booleanValue: false, claim: 'Gewinn gesunken', sourceUrl: 'https://example.com/ni', asOfDate: '2026-03-31', stale: false },
      { criterionKey: 'MOAT_ASSESSMENT', numericValue: null, booleanValue: null, claim: 'Starker Moat', sourceUrl: 'javascript:alert(1)', asOfDate: '2026-03-31', stale: false },
    ],
    lastResearchedAt: '2026-06-01', stale: false,
    ...over,
  }
}
const groups = [
  { key: 'tech', name: 'Technologie', sortOrder: 1, stockCount: 2 },
  { key: 'energy', name: 'Energie', sortOrder: 2, stockCount: 1 },
]

function res(status: number, body?: unknown) {
  return { ok: status < 400, status, headers: new Headers(), json: async () => body , text: async () => JSON.stringify(body )}
}

let putResponse: () => ReturnType<typeof res>
function mockBackend(opts: { snap?: unknown; status?: number; portfolio?: () => ReturnType<typeof res>; history?: () => ReturnType<typeof res> } = {}) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.endsWith('/history')) return Promise.resolve((opts.history ?? (() => res(200, { findings: [] })))())
    if (url.startsWith('/api/research/snapshots/') && init?.method === 'PUT') return Promise.resolve(putResponse())
    if (url.startsWith('/api/research/snapshots/')) return Promise.resolve(res(opts.status ?? 200, opts.snap ?? snapshot()))
    if (url.startsWith('/api/research/theses/')) return Promise.resolve(res(404, { error: 'Thesis not found' }))
    if (url === '/api/research/deep-analysis/quota') return Promise.resolve(res(200, { remainingToday: 5, limitPerDay: 5 }))
    if (url === '/api/research/groups') return Promise.resolve(res(200, groups))
    if (url === '/api/portfolio') return Promise.resolve((opts.portfolio ?? (() => res(200, [{ isin: 'US0378331005' }])))())
    if (url === '/api/auth/csrf') return Promise.resolve(res(200, {}))
    return Promise.resolve(res(404, {}))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderPage(user = admin, navigate = vi.fn()) {
  render(<ResearchDetailPage user={user} isin="US0378331005" navigate={navigate} />)
  return navigate
}

describe('ResearchDetailPage', () => {
  beforeEach(() => {
    putResponse = () => res(200, snapshot({ group: { key: 'energy', name: 'Energie', source: 'MANUAL' } }))
    mockBackend()
  })

  it('shows the last researched date, with a hint when stale', async () => {
    renderPage()
    expect(await screen.findByText('Last researched 01 Jun 2026')).toBeInTheDocument()
  })

  it('adds the re-research hint when the snapshot is stale', async () => {
    mockBackend({ snap: snapshot({ stale: true }) })
    renderPage()
    expect(await screen.findByText('Last researched 01 Jun 2026 – consider re-research')).toBeInTheDocument()
  })

  it('shows No findings yet without a research date', async () => {
    mockBackend({ snap: snapshot({ lastResearchedAt: null, findings: [] }) })
    renderPage()
    expect(await screen.findByText('No findings yet')).toBeInTheDocument()
  })

  it('marks only stale findings as Stale', async () => {
    const snap = snapshot()
    ;(snap.findings[0] as { stale: boolean }).stale = true
    mockBackend({ snap })
    renderPage()
    await screen.findByText('P/E is 12.5')
    expect(screen.getAllByText('Stale')).toHaveLength(1)
  })

  it('shows a collapsible history per criterion, newest first', async () => {
    mockBackend({
      history: () => res(200, {
        findings: [
          { criterionKey: 'PE_RATIO', numericValue: 11, booleanValue: null, claim: 'KGV 11', sourceUrl: 'https://example.com/1', asOfDate: '2025-12-31', replacedAt: '2026-03-02T10:00:00Z', origin: 'SYNC' },
          { criterionKey: 'PE_RATIO', numericValue: 9, booleanValue: null, claim: 'KGV 9', sourceUrl: 'https://example.com/2', asOfDate: '2025-06-30', replacedAt: '2025-12-31T10:00:00Z', origin: 'INGEST' },
        ],
      }),
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'History (2)' }))
    const items = screen.getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('KGV 11')
    expect(items[0]).toHaveTextContent('replaced on 02 Mar 2026')
    expect(items[1]).toHaveTextContent('KGV 9')
    expect(screen.getAllByRole('button', { name: /History \(/ })).toHaveLength(1)
  })

  it('shows no Groupnverlauf', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByText('Groupnverlauf')).not.toBeInTheDocument()
  })

  it('still renders the page when the history request fails (500)', async () => {
    mockBackend({ history: () => res(500, { error: 'boom' }) })
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Apple Inc.' })).toBeInTheDocument()
    expect(await screen.findByText('History could not be loaded')).toBeInTheDocument()
  })

  it('shows master data and group source', async () => {
    renderPage(guest)
    expect(await screen.findByRole('heading', { level: 1, name: 'Apple Inc.' })).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('US0378331005')).toBeInTheDocument()
    expect(screen.getByText('US')).toBeInTheDocument()
    expect(screen.getByText('Macht iPhones')).toBeInTheDocument()
    expect(screen.getByText(/Technologie/)).toBeInTheDocument()
    expect(screen.getByText(/AI suggestion/)).toBeInTheDocument()
    for (const h of ['A. Overview', 'B. Key figures', 'C. Qualitative findings', 'D. Quarterly reports', 'E. AI chat', 'F. Deep analysis'])
      expect(screen.getByRole('heading', { name: h })).toBeInTheDocument()
  })

  it('shows dash for missing description and country', async () => {
    mockBackend({ snap: snapshot({ country: null, businessDescription: '' }) })
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getAllByText('–').length).toBeGreaterThanOrEqual(2)
  })

  it('formats numeric and boolean values with claim, date and safe source link', async () => {
    renderPage()
    expect(await screen.findByText('12.5')).toBeInTheDocument()
    expect(screen.getByText('P/E is 12.5')).toBeInTheDocument()
    expect(screen.getAllByText('31 Mar 2026').length).toBeGreaterThan(0)
    expect(screen.getByText('yes')).toBeInTheDocument()
    expect(screen.getByText('no')).toBeInTheDocument()
    const link = screen.getAllByRole('link').find((a) => a.getAttribute('href') === 'https://example.com/pe')!
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('shows Not researched for missing criteria', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getAllByText('Not researched')).toHaveLength(14)
  })

  it('renders unsafe source urls as plain text, not links', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getByText('javascript:alert(1)')).toBeInTheDocument()
    expect(screen.getAllByRole('link').some((a) => a.getAttribute('href')?.startsWith('javascript'))).toBe(false)
  })

  it('shows portfolio badge only for held isin', async () => {
    renderPage()
    expect(await screen.findByText('The operator holds this position')).toBeInTheDocument()
  })

  it('omits badge when not held', async () => {
    mockBackend({ portfolio: () => res(200, []) })
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByText('The operator holds this position')).not.toBeInTheDocument()
  })

  it('omits badge when the portfolio request fails (500)', async () => {
    mockBackend({ portfolio: () => res(500, { error: 'x' }) })
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByText('The operator holds this position')).not.toBeInTheDocument()
  })

  it('shows the group once, as text with an edit button for ADMIN', async () => {
    renderPage(admin)
    expect(await screen.findByText('Technologie (AI suggestion)')).toBeInTheDocument()
    expect(screen.getAllByText(/Group/)).toHaveLength(1)
    expect(screen.queryByLabelText('Group:')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })

  it('lets ADMIN change the group via edit and save, then shows the text again', async () => {
    const fetchMock = mockBackend()
    renderPage(admin)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const select = screen.getByLabelText('Group:')
    expect(select.querySelectorAll('option')).toHaveLength(2)
    expect(select).toHaveValue('tech')
    expect(screen.queryByText('Technologie (AI suggestion)')).not.toBeInTheDocument()
    fireEvent.change(select, { target: { value: 'energy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Energie (manual)')).toBeInTheDocument()
    expect(screen.queryByLabelText('Group:')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    const put = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PUT')!
    expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({ groupKey: 'energy' })
  })

  it('saves nothing when the group is unchanged', async () => {
    const fetchMock = mockBackend()
    renderPage(admin)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Technologie (AI suggestion)')).toBeInTheDocument()
    expect(screen.queryByLabelText('Group:')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'PUT')).toBe(false)
  })

  it('shows save errors inline and stays in edit mode', async () => {
    putResponse = () => res(403, { error: 'Forbidden' })
    renderPage(admin)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Group:'), { target: { value: 'energy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Not allowed')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled())
    expect(screen.getByLabelText('Group:')).toBeInTheDocument()
  })

  it('gives USER no group controls', async () => {
    renderPage(guest)
    expect(await screen.findByText('Technologie (AI suggestion)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Group:')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('shows disabled placeholders', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    const upload = screen.getByRole('button', { name: 'Upload' })
    expect(upload).toBeDisabled()
    expect(upload).toHaveAttribute('title', 'Not available yet')
    expect(screen.getByRole('button', { name: 'Summarize quarterly reports' })).toBeDisabled()
    expect(screen.getByLabelText('Question about the stock')).toBeDisabled()
    expect(screen.getAllByText('Coming in a later phase').length).toBeGreaterThan(0)
  })

  it('renders the deep analysis section with its quota', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getByRole('button', { name: 'Run deep analysis' })).toBeInTheDocument()
    expect(await screen.findByText('Remaining today: 5 of 5 analyses')).toBeInTheDocument()
  })

  it('shows a message when there is no research', async () => {
    mockBackend({ status: 404, snap: { error: 'nf' } })
    renderPage()
    expect(await screen.findByText('No research for this stock yet')).toBeInTheDocument()
  })

  it('navigates back to the overview', async () => {
    const navigate = renderPage()
    await screen.findByRole('heading', { level: 1 })
    fireEvent.click(screen.getByRole('button', { name: 'Back to overview' }))
    expect(navigate).toHaveBeenCalledWith({ name: 'research' })
  })

  it('places the thesis section right after the overview', async () => {
    renderPage()
    const thesisHeading = await screen.findByRole('heading', { name: 'Investment thesis' })
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings.slice(0, 3)).toEqual(['A. Overview', 'Investment thesis', 'B. Key figures'])
    expect(thesisHeading).toBeInTheDocument()
  })

  it('does not show the previous ISIN result after switching to another ISIN', async () => {
    const base = mockBackend()
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith('/deep-analysis') && init?.method === 'POST') {
        return Promise.resolve(res(200, {
          status: 'NOT_IMPLEMENTED', message: 'Old analysis', greeting: 'Hello from AWS Lambda',
          functionName: 'deep-analysis-demo', functionVersion: '7', region: 'eu-central-1',
          memoryLimitMb: 256, awsRequestId: 'req-1', coldStart: false, invokedAt: '2026-11-12T13:45:30Z',
          source: 'LAMBDA', remainingToday: 4, limitPerDay: 5,
        }))
      }
      return base(url, init)
    })
    vi.stubGlobal('fetch', fetchMock)
    const { rerender } = render(<ResearchDetailPage user={admin} isin="US0378331005" navigate={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Run deep analysis' }))
    expect(await screen.findByText('Old analysis')).toBeInTheDocument()

    rerender(<ResearchDetailPage user={admin} isin="DE0007164600" navigate={vi.fn()} />)
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/research/snapshots/DE0007164600', expect.anything()))
    await screen.findByRole('button', { name: 'Run deep analysis' })
    expect(screen.queryByText('Old analysis')).not.toBeInTheDocument()
  })
})
