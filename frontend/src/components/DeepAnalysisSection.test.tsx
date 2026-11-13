import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DeepAnalysisSection } from './DeepAnalysisSection'

const ISIN = 'US0378331005'

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status })
}

function result(over: Record<string, unknown> = {}) {
  return {
    status: 'NOT_IMPLEMENTED',
    message: 'Noch nicht implementiert – hier wird künftig eine Tiefenanalyse für US0378331005 ausgegeben.',
    greeting: 'Hallo von AWS Lambda',
    functionName: 'deep-analysis-demo',
    functionVersion: '7',
    region: 'eu-central-1',
    memoryLimitMb: 256,
    awsRequestId: 'req-123',
    coldStart: true,
    invokedAt: '2026-11-12T13:45:30Z',
    source: 'LAMBDA',
    remainingToday: 4,
    limitPerDay: 5,
    ...over,
  }
}

let quotaResponse: () => Response
let postResponse: () => Promise<Response>

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok'
  quotaResponse = () => json(200, { remainingToday: 5, limitPerDay: 5 })
  postResponse = () => Promise.resolve(json(200, result()))
  vi.stubGlobal('fetch', vi.fn((_url: string, init?: RequestInit) => {
    if (init?.method === 'POST') return postResponse()
    return Promise.resolve(quotaResponse())
  }))
})

const start = () => screen.getByRole('button', { name: 'Tiefenanalyse starten' })

describe('DeepAnalysisSection', () => {
  it('shows the quota line', async () => {
    render(<DeepAnalysisSection isin={ISIN} />)
    expect(await screen.findByText('Heute noch 5 von 5 Analysen')).toBeInTheDocument()
  })

  it('runs the analysis and shows the result and the updated quota', async () => {
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 5 von 5 Analysen')
    fireEvent.click(start())
    expect(await screen.findByText('Hallo von AWS Lambda')).toBeInTheDocument()
    expect(screen.getByText(/Noch nicht implementiert – hier wird künftig/)).toBeInTheDocument()
    expect(screen.getByText('Funktion: deep-analysis-demo')).toBeInTheDocument()
    expect(screen.getByText('Version: 7')).toBeInTheDocument()
    expect(screen.getByText('Region: eu-central-1')).toBeInTheDocument()
    expect(screen.getByText('Speicher (MB): 256')).toBeInTheDocument()
    expect(screen.getByText('Request-ID: req-123')).toBeInTheDocument()
    expect(screen.getByText('Kaltstart: ja')).toBeInTheDocument()
    expect(screen.getByText('Ausgeführt am: 12.11.2026, 13:45')).toBeInTheDocument()
    expect(screen.getByText('Quelle: AWS Lambda')).toBeInTheDocument()
    expect(screen.getByText('Heute noch 4 von 5 Analysen')).toBeInTheDocument()
    const [path, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === 'POST',
    )!
    expect(path).toBe(`/api/research/snapshots/${ISIN}/deep-analysis`)
    expect((init as RequestInit).method).toBe('POST')
  })

  it('shows the local simulation source and Kaltstart nein', async () => {
    postResponse = () => Promise.resolve(json(200, result({ source: 'LOCAL_STUB', coldStart: false })))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 5 von 5 Analysen')
    fireEvent.click(start())
    expect(await screen.findByText('Quelle: lokale Simulation')).toBeInTheDocument()
    expect(screen.getByText('Kaltstart: nein')).toBeInTheDocument()
  })

  it('disables the button while the request is pending', async () => {
    let resolve!: (r: Response) => void
    postResponse = () => new Promise<Response>((r) => { resolve = r })
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 5 von 5 Analysen')
    fireEvent.click(start())
    await waitFor(() => expect(start()).toBeDisabled())
    resolve(json(200, result()))
    await waitFor(() => expect(start()).not.toBeDisabled())
  })

  it('disables the button when nothing is left', async () => {
    quotaResponse = () => json(200, { remainingToday: 0, limitPerDay: 5 })
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 0 von 5 Analysen')
    expect(start()).toBeDisabled()
  })

  it('disables the button after the last analysis was used', async () => {
    postResponse = () => Promise.resolve(json(200, result({ remainingToday: 0 })))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 5 von 5 Analysen')
    fireEvent.click(start())
    await screen.findByText('Heute noch 0 von 5 Analysen')
    expect(start()).toBeDisabled()
  })

  it('shows the backend message on 429', async () => {
    postResponse = () => Promise.resolve(json(429, { error: 'Tageslimit für die Tiefenanalyse erreicht' }))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 5 von 5 Analysen')
    fireEvent.click(start())
    expect(await screen.findByRole('alert')).toHaveTextContent('Tageslimit für die Tiefenanalyse erreicht')
  })

  it('shows the unavailable message on 502', async () => {
    postResponse = () => Promise.resolve(json(502, { error: 'Tiefenanalyse nicht verfügbar' }))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Heute noch 5 von 5 Analysen')
    fireEvent.click(start())
    expect(await screen.findByRole('alert')).toHaveTextContent('Tiefenanalyse nicht verfügbar')
  })

  it('stays usable without a quota line when the quota load fails', async () => {
    quotaResponse = () => json(500, { error: 'boom' })
    render(<DeepAnalysisSection isin={ISIN} />)
    await waitFor(() => expect(fetch).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 0))
    expect(screen.queryByText(/Heute noch/)).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(start()).not.toBeDisabled()
    fireEvent.click(start())
    expect(await screen.findByText('Hallo von AWS Lambda')).toBeInTheDocument()
    expect(screen.getByText('Heute noch 4 von 5 Analysen')).toBeInTheDocument()
  })
})
