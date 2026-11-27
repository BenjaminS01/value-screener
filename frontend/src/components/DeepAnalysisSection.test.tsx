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
    message: 'Not implemented yet – a deep analysis for US0378331005 will appear here later.',
    greeting: 'Hello from AWS Lambda',
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

const start = () => screen.getByRole('button', { name: 'Run deep analysis' })

describe('DeepAnalysisSection', () => {
  it('shows the quota line', async () => {
    render(<DeepAnalysisSection isin={ISIN} />)
    expect(await screen.findByText('Remaining today: 5 of 5 analyses')).toBeInTheDocument()
  })

  it('runs the analysis and shows the result and the updated quota', async () => {
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 5 of 5 analyses')
    fireEvent.click(start())
    expect(await screen.findByText('Hello from AWS Lambda')).toBeInTheDocument()
    expect(screen.getByText(/Not implemented yet – a deep analysis/)).toBeInTheDocument()
    expect(screen.getByText('Function: deep-analysis-demo')).toBeInTheDocument()
    expect(screen.getByText('Version: 7')).toBeInTheDocument()
    expect(screen.getByText('Region: eu-central-1')).toBeInTheDocument()
    expect(screen.getByText('Memory (MB): 256')).toBeInTheDocument()
    expect(screen.getByText('Request ID: req-123')).toBeInTheDocument()
    expect(screen.getByText('Cold start: yes')).toBeInTheDocument()
    expect(screen.getByText('Invoked at: 12 Nov 2026, 13:45')).toBeInTheDocument()
    expect(screen.getByText('Source: AWS Lambda')).toBeInTheDocument()
    expect(screen.getByText('Remaining today: 4 of 5 analyses')).toBeInTheDocument()
    const [path, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === 'POST',
    )!
    expect(path).toBe(`/api/research/snapshots/${ISIN}/deep-analysis`)
    expect((init as RequestInit).method).toBe('POST')
  })

  it('shows the local simulation source and cold start no', async () => {
    postResponse = () => Promise.resolve(json(200, result({ source: 'LOCAL_STUB', coldStart: false })))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 5 of 5 analyses')
    fireEvent.click(start())
    expect(await screen.findByText('Source: local simulation')).toBeInTheDocument()
    expect(screen.getByText('Cold start: no')).toBeInTheDocument()
  })

  it('disables the button while the request is pending', async () => {
    let resolve!: (r: Response) => void
    postResponse = () => new Promise<Response>((r) => { resolve = r })
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 5 of 5 analyses')
    fireEvent.click(start())
    await waitFor(() => expect(start()).toBeDisabled())
    resolve(json(200, result()))
    await waitFor(() => expect(start()).not.toBeDisabled())
  })

  it('disables the button when nothing is left', async () => {
    quotaResponse = () => json(200, { remainingToday: 0, limitPerDay: 5 })
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 0 of 5 analyses')
    expect(start()).toBeDisabled()
  })

  it('disables the button after the last analysis was used', async () => {
    postResponse = () => Promise.resolve(json(200, result({ remainingToday: 0 })))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 5 of 5 analyses')
    fireEvent.click(start())
    await screen.findByText('Remaining today: 0 of 5 analyses')
    expect(start()).toBeDisabled()
  })

  it('shows the backend message on 429', async () => {
    postResponse = () => Promise.resolve(json(429, { error: 'Daily limit for deep analysis reached' }))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 5 of 5 analyses')
    fireEvent.click(start())
    expect(await screen.findByRole('alert')).toHaveTextContent('Daily limit for deep analysis reached')
  })

  it('shows the unavailable message on 502', async () => {
    postResponse = () => Promise.resolve(json(502, { error: 'Deep analysis unavailable' }))
    render(<DeepAnalysisSection isin={ISIN} />)
    await screen.findByText('Remaining today: 5 of 5 analyses')
    fireEvent.click(start())
    expect(await screen.findByRole('alert')).toHaveTextContent('Deep analysis unavailable')
  })

  it('stays usable without a quota line when the quota load fails', async () => {
    quotaResponse = () => json(500, { error: 'boom' })
    render(<DeepAnalysisSection isin={ISIN} />)
    await waitFor(() => expect(fetch).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 0))
    expect(screen.queryByText(/Remaining today/)).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(start()).not.toBeDisabled()
    fireEvent.click(start())
    expect(await screen.findByText('Hello from AWS Lambda')).toBeInTheDocument()
    expect(screen.getByText('Remaining today: 4 of 5 analyses')).toBeInTheDocument()
  })
})
