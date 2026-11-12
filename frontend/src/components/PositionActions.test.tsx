import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PositionActions } from './PositionActions'
import { ApiError } from '../api/apiClient'
import * as portfolioApi from '../api/portfolioApi'

vi.mock('../api/portfolioApi')

const position: portfolioApi.AdminPosition = {
  ticker: 'AAPL',
  isin: 'US0378331005',
  companyName: 'Apple Inc.',
  group: null,
  quantity: 10,
  entryPrice: 150.5,
  purchaseDate: '2026-01-15',
}

describe('PositionActions', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('sells the entered quantity and reports the change', async () => {
    vi.mocked(portfolioApi.sellPosition).mockResolvedValue(undefined)
    const onChanged = vi.fn()
    render(<PositionActions position={position} onChanged={onChanged} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(portfolioApi.sellPosition).toHaveBeenCalledWith('US0378331005', 4)
  })

  it('prefills the correct form with current values and submits', async () => {
    vi.mocked(portfolioApi.correctPosition).mockResolvedValue(undefined)
    const onChanged = vi.fn()
    render(<PositionActions position={position} onChanged={onChanged} />)
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    expect(screen.getByLabelText('Ticker')).toHaveValue('AAPL')
    expect(screen.getByLabelText('Unternehmen')).toHaveValue('Apple Inc.')
    expect(screen.getByLabelText('Stückzahl')).toHaveValue('10')
    expect(screen.getByLabelText('Einstandskurs')).toHaveValue('150.5')
    expect(screen.getByLabelText('Kaufdatum')).toHaveValue('2026-01-15')
    fireEvent.change(screen.getByLabelText('Stückzahl'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(portfolioApi.correctPosition).toHaveBeenCalledWith('US0378331005', {
      ticker: 'AAPL',
      companyName: 'Apple Inc.',
      quantity: 12,
      entryPrice: 150.5,
      purchaseDate: '2026-01-15',
    })
  })

  it.each(['sell', 'correct', 'delete'])('on 409 during %s reports the conflict text via onChanged', async (kind) => {
    const conflict = new ApiError('conflict', 409)
    vi.mocked(portfolioApi.sellPosition).mockRejectedValue(conflict)
    vi.mocked(portfolioApi.correctPosition).mockRejectedValue(conflict)
    vi.mocked(portfolioApi.deletePosition).mockRejectedValue(conflict)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onChanged = vi.fn()
    render(<PositionActions position={position} onChanged={onChanged} />)
    if (kind === 'sell') {
      fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
      fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '1' } })
      fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    } else if (kind === 'correct') {
      fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    } else {
      fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    }
    await waitFor(() =>
      expect(onChanged).toHaveBeenCalledWith('Die Position wurde inzwischen geändert, bitte neu laden'),
    )
  })

  it('accepts a German decimal comma when selling', async () => {
    vi.mocked(portfolioApi.sellPosition).mockResolvedValue(undefined)
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '2,5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    await waitFor(() => expect(portfolioApi.sellPosition).toHaveBeenCalledWith('US0378331005', 2.5))
  })

  it('does not sell for an empty quantity and shows a message', async () => {
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.sellPosition).not.toHaveBeenCalled()
  })

  it('does not sell for quantity 0', async () => {
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.sellPosition).not.toHaveBeenCalled()
  })

  it.each([
    ['Stückzahl', '0'],
    ['Einstandskurs', '-1'],
  ])('does not correct for %s %s', async (label, value) => {
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.correctPosition).not.toHaveBeenCalled()
  })

  it('resets the sell quantity when sell mode is opened again', async () => {
    vi.mocked(portfolioApi.sellPosition).mockResolvedValue(undefined)
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    await waitFor(() => expect(portfolioApi.sellPosition).toHaveBeenCalled())
    await waitFor(() => expect(screen.queryByLabelText('Verkaufsmenge')).not.toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    expect(screen.getByLabelText('Verkaufsmenge')).toHaveValue('')
  })

  it('does not correct for an invalid number', async () => {
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    fireEvent.change(screen.getByLabelText('Einstandskurs'), { target: { value: 'x' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.correctPosition).not.toHaveBeenCalled()
  })

  it('deletes after confirmation', async () => {
    vi.mocked(portfolioApi.deletePosition).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onChanged = vi.fn()
    render(<PositionActions position={position} onChanged={onChanged} />)
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(portfolioApi.deletePosition).toHaveBeenCalledWith('US0378331005')
  })

  it('does not delete when confirmation is declined', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    expect(portfolioApi.deletePosition).not.toHaveBeenCalled()
  })

  it('shows Keine Berechtigung on 403', async () => {
    vi.mocked(portfolioApi.sellPosition).mockRejectedValue(new ApiError('Forbidden', 403))
    render(<PositionActions position={position} onChanged={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verkaufen' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verkauf bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Keine Berechtigung')
  })
})
