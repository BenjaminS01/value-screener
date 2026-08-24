import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResearchLibraryPage } from './ResearchLibraryPage'

function mockLibrary() {
  return (url: string) => {
    if (url === '/api/research/snapshots') {
      return Promise.resolve({
        ok: true,
        json: async () => [
          {
            id: 1,
            ticker: 'AAPL',
            isin: 'US0378331005',
            companyName: 'Apple Inc.',
            sector: 'Technology',
            country: 'US',
            businessDescription: 'Consumer electronics and services.',
            findings: [],
          },
          {
            id: 2,
            ticker: 'NVDA',
            isin: 'US67066G1040',
            companyName: 'NVIDIA Corp.',
            sector: 'Semiconductors',
            country: 'US',
            businessDescription: 'Graphics and AI chips.',
            findings: [],
          },
        ],
      })
    }
    if (url === '/api/portfolio/public') {
      return Promise.resolve({
        ok: true,
        json: async () => [{ ticker: 'AAPL', companyName: 'Apple Inc.', isin: 'US0378331005' }],
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

describe('ResearchLibraryPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('shows the English page title', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockLibrary())

    render(<ResearchLibraryPage />)

    expect(await screen.findByRole('heading', { name: 'Research' })).toBeInTheDocument()
  })

  it('lists all researched companies', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockLibrary())

    render(<ResearchLibraryPage />)

    expect(await screen.findByText(/Apple Inc\. \(AAPL\)/)).toBeInTheDocument()
    expect(await screen.findByText(/NVIDIA Corp\. \(NVDA\)/)).toBeInTheDocument()
  })

  it('shows an error message when the request fails', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => [],
    })

    render(<ResearchLibraryPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/Failed to load .*: 500/)
  })

  it('expands a company to show its research findings', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockLibrary())

    render(<ResearchLibraryPage />)

    fireEvent.click(await screen.findByText(/Apple Inc\. \(AAPL\)/))

    expect(await screen.findByText('Technology')).toBeInTheDocument()
  })

  it('shows an interest-conflict hint for a company the operator also owns', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockLibrary())

    render(<ResearchLibraryPage />)

    const appleItem = (await screen.findByText(/Apple Inc\. \(AAPL\)/)).closest('li') as HTMLElement
    const nvidiaItem = (await screen.findByText(/NVIDIA Corp\. \(NVDA\)/)).closest('li') as HTMLElement

    expect(appleItem).toHaveTextContent(/operator/i)
    expect(nvidiaItem).not.toHaveTextContent(/operator/i)
  })

  it('offers a picker of known tickers and shows deep-research placeholder once one is selected', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(mockLibrary())

    render(<ResearchLibraryPage />)

    const select = await screen.findByLabelText('Select a stock')
    expect(screen.queryByRole('button', { name: /Run deep research/ })).not.toBeInTheDocument()

    fireEvent.change(select, { target: { value: 'NVDA' } })

    expect(screen.getByRole('button', { name: /Run deep research/ })).toBeInTheDocument()
  })
})
