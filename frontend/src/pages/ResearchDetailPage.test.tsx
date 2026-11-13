import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResearchDetailPage } from './ResearchDetailPage'
import type { AuthUser } from '../api/authApi'

const admin: AuthUser = { username: 'ben', role: 'ADMIN' }
const guest: AuthUser = { username: 'guest', role: 'USER' }

function snapshot(over: Record<string, unknown> = {}) {
  return {
    id: 1, ticker: 'AAPL', isin: 'US0378331005', companyName: 'Apple Inc.', country: 'US',
    businessDescription: 'Macht iPhones',
    group: { key: 'tech', name: 'Technologie', source: 'AI' },
    findings: [
      { criterionKey: 'PE_RATIO', numericValue: 12.5, booleanValue: null, claim: 'KGV liegt bei 12,5', sourceUrl: 'https://example.com/pe', asOfDate: '2026-03-31', stale: false },
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
    if (url.endsWith('/history')) return Promise.resolve((opts.history ?? (() => res(200, { findings: [], groups: [] })))())
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
    expect(await screen.findByText('Zuletzt recherchiert 01.06.2026')).toBeInTheDocument()
  })

  it('adds the re-research hint when the snapshot is stale', async () => {
    mockBackend({ snap: snapshot({ stale: true }) })
    renderPage()
    expect(await screen.findByText('Zuletzt recherchiert 01.06.2026 – Neu-Recherche erwägen')).toBeInTheDocument()
  })

  it('shows Noch keine Befunde without a research date', async () => {
    mockBackend({ snap: snapshot({ lastResearchedAt: null, findings: [] }) })
    renderPage()
    expect(await screen.findByText('Noch keine Befunde')).toBeInTheDocument()
  })

  it('marks only stale findings as veraltet', async () => {
    const snap = snapshot()
    ;(snap.findings[0] as { stale: boolean }).stale = true
    mockBackend({ snap })
    renderPage()
    await screen.findByText('KGV liegt bei 12,5')
    expect(screen.getAllByText('veraltet')).toHaveLength(1)
  })

  it('shows a collapsible history per criterion, newest first', async () => {
    mockBackend({
      history: () => res(200, {
        findings: [
          { criterionKey: 'PE_RATIO', numericValue: 11, booleanValue: null, claim: 'KGV 11', sourceUrl: 'https://example.com/1', asOfDate: '2025-12-31', replacedAt: '2026-03-02T10:00:00Z', origin: 'SYNC' },
          { criterionKey: 'PE_RATIO', numericValue: 9, booleanValue: null, claim: 'KGV 9', sourceUrl: 'https://example.com/2', asOfDate: '2025-06-30', replacedAt: '2025-12-31T10:00:00Z', origin: 'INGEST' },
        ],
        groups: [],
      }),
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Verlauf (2)' }))
    const items = screen.getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('KGV 11')
    expect(items[0]).toHaveTextContent('ersetzt am 02.03.2026')
    expect(items[1]).toHaveTextContent('KGV 9')
    expect(screen.getAllByRole('button', { name: /Verlauf \(/ })).toHaveLength(1)
  })

  it('lists the group history', async () => {
    mockBackend({
      history: () => res(200, {
        findings: [],
        groups: [
          { previousGroup: { key: 'semi', name: 'Halbleiter' }, previousSource: 'AI', newGroup: { key: 'sw', name: 'Software' }, newSource: 'MANUAL', changedAt: '2026-11-03T08:00:00Z', origin: 'MANUAL' },
          { previousGroup: null, previousSource: null, newGroup: { key: 'semi', name: 'Halbleiter' }, newSource: 'AI', changedAt: '2026-10-01T08:00:00Z', origin: 'INGEST' },
        ],
      }),
    })
    renderPage()
    expect(await screen.findByText('Gruppenverlauf')).toBeInTheDocument()
    expect(screen.getByText('Halbleiter → Software (manuell) am 03.11.2026')).toBeInTheDocument()
    expect(screen.getByText('— → Halbleiter (KI-Vorschlag) am 01.10.2026')).toBeInTheDocument()
  })

  it('omits Gruppenverlauf without group entries', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByText('Gruppenverlauf')).not.toBeInTheDocument()
  })

  it('still renders the page when the history request fails (500)', async () => {
    mockBackend({ history: () => res(500, { error: 'boom' }) })
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Apple Inc.' })).toBeInTheDocument()
    expect(await screen.findByText('Verlauf konnte nicht geladen werden')).toBeInTheDocument()
  })

  it('shows master data and group source', async () => {
    renderPage(guest)
    expect(await screen.findByRole('heading', { level: 1, name: 'Apple Inc.' })).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('US0378331005')).toBeInTheDocument()
    expect(screen.getByText('US')).toBeInTheDocument()
    expect(screen.getByText('Macht iPhones')).toBeInTheDocument()
    expect(screen.getByText(/Technologie/)).toBeInTheDocument()
    expect(screen.getByText(/KI-Vorschlag/)).toBeInTheDocument()
    for (const h of ['A. Überblick', 'B. Kennzahlen', 'C. Qualitative Befunde', 'D. Quartalsberichte', 'E. KI-Chat', 'F. Tiefenanalyse'])
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
    expect(await screen.findByText('12,5')).toBeInTheDocument()
    expect(screen.getByText('KGV liegt bei 12,5')).toBeInTheDocument()
    expect(screen.getAllByText('31.03.2026').length).toBeGreaterThan(0)
    expect(screen.getByText('ja')).toBeInTheDocument()
    expect(screen.getByText('nein')).toBeInTheDocument()
    const link = screen.getAllByRole('link').find((a) => a.getAttribute('href') === 'https://example.com/pe')!
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('shows nicht recherchiert for missing criteria', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getAllByText('nicht recherchiert')).toHaveLength(14)
  })

  it('renders unsafe source urls as plain text, not links', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getByText('javascript:alert(1)')).toBeInTheDocument()
    expect(screen.getAllByRole('link').some((a) => a.getAttribute('href')?.startsWith('javascript'))).toBe(false)
  })

  it('shows portfolio badge only for held isin', async () => {
    renderPage()
    expect(await screen.findByText('Der Betreiber hält diese Position')).toBeInTheDocument()
  })

  it('omits badge when not held', async () => {
    mockBackend({ portfolio: () => res(200, []) })
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByText('Der Betreiber hält diese Position')).not.toBeInTheDocument()
  })

  it('omits badge when the portfolio request fails (500)', async () => {
    mockBackend({ portfolio: () => res(500, { error: 'x' }) })
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByText('Der Betreiber hält diese Position')).not.toBeInTheDocument()
  })

  it('lets ADMIN change the group', async () => {
    const fetchMock = mockBackend()
    renderPage(admin)
    const select = await screen.findByLabelText('Gruppe')
    expect(select.querySelectorAll('option')).toHaveLength(2)
    fireEvent.change(select, { target: { value: 'energy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText(/manuell/)).toBeInTheDocument()
    const put = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PUT')!
    expect(JSON.parse((put[1] as RequestInit).body as string)).toEqual({ groupKey: 'energy' })
  })

  it('refreshes the group history after ADMIN saves a group', async () => {
    let calls = 0
    mockBackend({
      history: () => {
        calls += 1
        return res(200, {
          findings: [],
          groups: calls === 1 ? [] : [
            { previousGroup: { key: 'tech', name: 'Technologie' }, previousSource: 'AI', newGroup: { key: 'energy', name: 'Energie' }, newSource: 'MANUAL', changedAt: '2026-11-05T08:00:00Z', origin: 'MANUAL' },
          ],
        })
      },
    })
    renderPage(admin)
    const select = await screen.findByLabelText('Gruppe')
    expect(screen.queryByText('Gruppenverlauf')).not.toBeInTheDocument()
    fireEvent.change(select, { target: { value: 'energy' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Gruppenverlauf')).toBeInTheDocument()
    expect(screen.getByText('Technologie → Energie (manuell) am 05.11.2026')).toBeInTheDocument()
  })

  it('shows save errors inline', async () => {
    putResponse = () => res(403, { error: 'Forbidden' })
    renderPage(admin)
    await screen.findByLabelText('Gruppe')
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Keine Berechtigung')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled())
  })

  it('gives USER no group controls', async () => {
    renderPage(guest)
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByLabelText('Gruppe')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
  })

  it('shows disabled placeholders', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    const upload = screen.getByRole('button', { name: 'Upload' })
    expect(upload).toBeDisabled()
    expect(upload).toHaveAttribute('title', 'Noch nicht verfügbar')
    expect(screen.getByRole('button', { name: 'Quartalsberichte zusammenfassen' })).toBeDisabled()
    expect(screen.getByLabelText('Frage zur Aktie')).toBeDisabled()
    expect(screen.getAllByText('Folgt in einer späteren Phase').length).toBeGreaterThan(0)
  })

  it('renders the deep analysis section with its quota', async () => {
    renderPage()
    await screen.findByRole('heading', { level: 1 })
    expect(screen.getByRole('button', { name: 'Tiefenanalyse starten' })).toBeInTheDocument()
    expect(await screen.findByText('Heute noch 5 von 5 Analysen')).toBeInTheDocument()
  })

  it('shows a message when there is no research', async () => {
    mockBackend({ status: 404, snap: { error: 'nf' } })
    renderPage()
    expect(await screen.findByText('Für diese Aktie gibt es noch keine Recherche')).toBeInTheDocument()
  })

  it('navigates back to the overview', async () => {
    const navigate = renderPage()
    await screen.findByRole('heading', { level: 1 })
    fireEvent.click(screen.getByRole('button', { name: 'Zurück zur Übersicht' }))
    expect(navigate).toHaveBeenCalledWith({ name: 'research' })
  })

  it('places the thesis section right after the overview', async () => {
    renderPage()
    const thesisHeading = await screen.findByRole('heading', { name: 'Investment-These' })
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings.slice(0, 3)).toEqual(['A. Überblick', 'Investment-These', 'B. Kennzahlen'])
    expect(thesisHeading).toBeInTheDocument()
  })
})
