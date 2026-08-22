import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortfolioPage } from './PortfolioPage'

function mockPortfolioAndResearch() {
  return (url: string) => {
    if (url === '/api/portfolio/public') {
      return Promise.resolve({
        ok: true,
        json: async () => [
          { ticker: 'AAPL', companyName: 'Apple Inc.', isin: 'US0378331005' },
          { ticker: 'MSFT', companyName: 'Microsoft Corp.', isin: 'US5949181045' },
        ],
      })
    }
    if (url === '/api/research/snapshots/US0378331005') {
      return Promise.resolve({
        ok: true,
        json: async () => ({
          id: 1,
          ticker: 'AAPL',
          isin: 'US0378331005',
          companyName: 'Apple Inc.',
          sector: 'Technology',
          country: 'US',
          businessDescription: 'Consumer electronics and services.',
          findings: [],
        }),
      })
    }
    return Promise.resolve({ ok: false, status: 404, json: async () => ({}) })
  }
}

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('renders public tickers and company names returned by the backend', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockPortfolioAndResearch())

    render(<PortfolioPage />)

    expect(await screen.findByText(/Apple Inc\. \(AAPL\)/)).toBeInTheDocument()
    expect(await screen.findByText(/Microsoft Corp\. \(MSFT\)/)).toBeInTheDocument()
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

  it('shows the English page title', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockPortfolioAndResearch())

    render(<PortfolioPage />)

    expect(await screen.findByRole('heading', { name: 'My Portfolio' })).toBeInTheDocument()
  })

  it('expands a position to show its research findings and the deep-research placeholder', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockPortfolioAndResearch())

    render(<PortfolioPage />)

    fireEvent.click(await screen.findByText(/Apple Inc\. \(AAPL\)/))

    expect(await screen.findByText('Technology')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Run deep research/ })).toBeInTheDocument()
  })
})
