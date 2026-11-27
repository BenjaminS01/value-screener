import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AddPositionForm } from './AddPositionForm'
import { ApiError } from '../api/apiClient'
import * as portfolioApi from '../api/portfolioApi'

vi.mock('../api/portfolioApi')

function fill() {
  fireEvent.change(screen.getByLabelText('Ticker'), { target: { value: 'AAPL' } })
  fireEvent.change(screen.getByLabelText('ISIN'), { target: { value: 'US0378331005' } })
  fireEvent.change(screen.getByLabelText('Company'), { target: { value: 'Apple Inc.' } })
  fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '10' } })
  fireEvent.change(screen.getByLabelText('Entry price'), { target: { value: '150.5' } })
  fireEvent.change(screen.getByLabelText('Purchase date'), { target: { value: '2026-01-15' } })
}

describe('AddPositionForm', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('submits numbers to buyPosition and calls onAdded', async () => {
    vi.mocked(portfolioApi.buyPosition).mockResolvedValue(undefined)
    const onAdded = vi.fn()
    render(<AddPositionForm onAdded={onAdded} />)
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    await waitFor(() => expect(onAdded).toHaveBeenCalled())
    expect(portfolioApi.buyPosition).toHaveBeenCalledWith({
      ticker: 'AAPL',
      isin: 'US0378331005',
      companyName: 'Apple Inc.',
      quantity: 10,
      entryPrice: 150.5,
      purchaseDate: '2026-01-15',
    })
  })

  it('disables the button while the request is pending', async () => {
    let resolve!: () => void
    vi.mocked(portfolioApi.buyPosition).mockReturnValue(new Promise<void>((r) => (resolve = r)))
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    expect(await screen.findByRole('button', { name: 'Buy' })).toBeDisabled()
    resolve()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Buy' })).toBeEnabled())
  })

  it('shows the backend error message', async () => {
    vi.mocked(portfolioApi.buyPosition).mockRejectedValue(new ApiError('Position exists', 400))
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Position exists')
  })

  it('shows Not allowed on 403', async () => {
    vi.mocked(portfolioApi.buyPosition).mockRejectedValue(new ApiError('Forbidden', 403))
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Not allowed')
  })

  it('accepts a German decimal comma and sends a number', async () => {
    vi.mocked(portfolioApi.buyPosition).mockResolvedValue(undefined)
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.change(screen.getByLabelText('Entry price'), { target: { value: '150,5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    await waitFor(() => expect(portfolioApi.buyPosition).toHaveBeenCalled())
    expect(vi.mocked(portfolioApi.buyPosition).mock.calls[0][0].entryPrice).toBe(150.5)
  })

  it.each([
    ['Quantity', '0'],
    ['Entry price', '-5'],
  ])('does not send for %s %s', async (label, value) => {
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Please enter a valid number')
    expect(portfolioApi.buyPosition).not.toHaveBeenCalled()
  })

  it('does not send and shows a message for an invalid number', async () => {
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: 'abc' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Please enter a valid number')
    expect(portfolioApi.buyPosition).not.toHaveBeenCalled()
  })

  it('sends a filled purchase reason', async () => {
    vi.mocked(portfolioApi.buyPosition).mockResolvedValue(undefined)
    const onAdded = vi.fn()
    render(<AddPositionForm onAdded={onAdded} />)
    fill()
    fireEvent.change(screen.getByLabelText('Why are you buying? (optional, appended to the investment thesis)'), {
      target: { value: 'Attractively valued' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    await waitFor(() => expect(onAdded).toHaveBeenCalled())
    expect(portfolioApi.buyPosition).toHaveBeenCalledWith(
      expect.objectContaining({ purchaseReason: 'Attractively valued' }),
    )
  })

  it('omits an empty purchase reason', async () => {
    vi.mocked(portfolioApi.buyPosition).mockResolvedValue(undefined)
    const onAdded = vi.fn()
    render(<AddPositionForm onAdded={onAdded} />)
    fill()
    fireEvent.change(screen.getByLabelText('Why are you buying? (optional, appended to the investment thesis)'), {
      target: { value: '   ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    await waitFor(() => expect(onAdded).toHaveBeenCalled())
    expect(vi.mocked(portfolioApi.buyPosition).mock.calls[0][0]).not.toHaveProperty('purchaseReason')
  })

  it('shows the 422 message for a too long thesis', async () => {
    vi.mocked(portfolioApi.buyPosition).mockRejectedValue(new ApiError('Thesis text too long', 422))
    render(<AddPositionForm onAdded={vi.fn()} />)
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Buy' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Thesis text too long')
  })
})
