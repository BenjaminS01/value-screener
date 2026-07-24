import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortfolioPage } from './PortfolioPage'

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('renders public tickers and company names returned by the backend', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => [
        { ticker: 'AAPL', companyName: 'Apple Inc.' },
        { ticker: 'MSFT', companyName: 'Microsoft Corp.' },
      ],
    })

    render(<PortfolioPage />)

    expect(await screen.findByText('Apple Inc. (AAPL)')).toBeInTheDocument()
    expect(await screen.findByText('Microsoft Corp. (MSFT)')).toBeInTheDocument()
  })

  it('shows an error message when the request fails', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => [],
    })

    render(<PortfolioPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load portfolio: 500')
  })
})
