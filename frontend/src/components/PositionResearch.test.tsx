import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PositionResearch } from './PositionResearch'

describe('PositionResearch', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('renders the company context header and findings for a researched position', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 1,
        ticker: 'AAPL',
        isin: 'US0378331005',
        companyName: 'Apple Inc.',
        sector: 'Technology',
        country: 'US',
        businessDescription: 'Consumer electronics and services.',
        findings: [
          {
            criterionKey: 'PE_RATIO',
            numericValue: 28.5,
            booleanValue: null,
            claim: 'P/E of 28.5 as of the latest filing.',
            sourceUrl: 'https://example.com/aapl',
            asOfDate: '2026-08-01',
          },
        ],
      }),
    })

    render(<PositionResearch isin="US0378331005" />)

    expect(await screen.findByText('Technology')).toBeInTheDocument()
    expect(screen.getByText('US')).toBeInTheDocument()
    expect(screen.getByText('Consumer electronics and services.')).toBeInTheDocument()
    expect(screen.getByText('P/E Ratio')).toBeInTheDocument()
    expect(screen.getByText('P/E of 28.5 as of the latest filing.')).toBeInTheDocument()
  })

  it('shows a fallback message when the position has not been researched yet', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 404,
      json: async () => ({}),
    })

    render(<PositionResearch isin="US9999999999" />)

    expect(await screen.findByText('Not yet researched.')).toBeInTheDocument()
  })

  it('shows an error message when the request fails', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    })

    render(<PositionResearch isin="US0378331005" />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Failed to load research snapshot: 500',
    )
  })
})
