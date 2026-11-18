import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminPositionRow } from './AdminPositionRow'
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

function renderRow(onChanged: (m?: string) => void = vi.fn(), onOpen: (isin: string) => void = vi.fn()) {
  render(
    <table>
      <tbody>
        <AdminPositionRow position={position} onOpen={onOpen} onChanged={onChanged} />
      </tbody>
    </table>,
  )
}

describe('AdminPositionRow', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('sells the entered quantity and reports the change', async () => {
    vi.mocked(portfolioApi.sellPosition).mockResolvedValue(undefined)
    const onChanged = vi.fn()
    renderRow(onChanged)
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(portfolioApi.sellPosition).toHaveBeenCalledWith('US0378331005', 4)
  })

  it('prefills the correct form with current values and submits', async () => {
    vi.mocked(portfolioApi.correctPosition).mockResolvedValue(undefined)
    const onChanged = vi.fn()
    renderRow(onChanged)
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

  it('shows the values as text; Korrigieren turns the same cells into inputs', () => {
    renderRow()
    expect(screen.getByRole('button', { name: 'Apple Inc.' })).toBeInTheDocument()
    expect(screen.getByText('150,50')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    expect(screen.getAllByRole('row')).toHaveLength(1)
    expect(screen.getAllByLabelText('Ticker')).toHaveLength(1)
    expect(screen.queryByText('150,50')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Korrigieren' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeInTheDocument()
  })

  it('saves nothing when no value changed and shows the text again', async () => {
    const onChanged = vi.fn()
    renderRow(onChanged)
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('150,50')).toBeInTheDocument()
    expect(screen.queryByLabelText('Ticker')).not.toBeInTheDocument()
    expect(portfolioApi.correctPosition).not.toHaveBeenCalled()
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('Abbrechen leaves edit mode without saving', () => {
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    fireEvent.change(screen.getByLabelText('Stückzahl'), { target: { value: '99' } })
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(portfolioApi.correctPosition).not.toHaveBeenCalled()
  })

  it('opens the position via the company name', () => {
    const onOpen = vi.fn()
    renderRow(vi.fn(), onOpen)
    fireEvent.click(screen.getByRole('button', { name: 'Apple Inc.' }))
    expect(onOpen).toHaveBeenCalledWith('US0378331005')
  })

  it.each(['sell', 'correct', 'delete'])('on 409 during %s reports the conflict text via onChanged', async (kind) => {
    const conflict = new ApiError('conflict', 409)
    vi.mocked(portfolioApi.sellPosition).mockRejectedValue(conflict)
    vi.mocked(portfolioApi.correctPosition).mockRejectedValue(conflict)
    vi.mocked(portfolioApi.deletePosition).mockRejectedValue(conflict)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onChanged = vi.fn()
    renderRow(onChanged)
    if (kind === 'sell') {
      fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
      fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '1' } })
      fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    } else if (kind === 'correct') {
      fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
      fireEvent.change(screen.getByLabelText('Stückzahl'), { target: { value: '11' } })
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
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '2,5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    await waitFor(() => expect(portfolioApi.sellPosition).toHaveBeenCalledWith('US0378331005', 2.5))
  })

  it('does not sell for an empty quantity and shows a message', async () => {
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.sellPosition).not.toHaveBeenCalled()
  })

  it('does not sell for quantity 0', async () => {
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.sellPosition).not.toHaveBeenCalled()
  })

  it.each([
    ['Stückzahl', '0'],
    ['Einstandskurs', '-1'],
  ])('does not correct for %s %s', async (label, value) => {
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Korrigieren' }))
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte eine gültige Zahl eingeben')
    expect(portfolioApi.correctPosition).not.toHaveBeenCalled()
  })

  it('resets the sell quantity when sell mode is opened again', async () => {
    vi.mocked(portfolioApi.sellPosition).mockResolvedValue(undefined)
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    await waitFor(() => expect(portfolioApi.sellPosition).toHaveBeenCalled())
    await waitFor(() => expect(screen.queryByLabelText('Verkaufsmenge')).not.toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    expect(screen.getByLabelText('Verkaufsmenge')).toHaveValue('')
  })

  it('does not correct for an invalid number', async () => {
    renderRow()
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
    renderRow(onChanged)
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(portfolioApi.deletePosition).toHaveBeenCalledWith('US0378331005')
  })

  it('does not delete when confirmation is declined', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    expect(portfolioApi.deletePosition).not.toHaveBeenCalled()
  })

  it('shows Keine Berechtigung on 403', async () => {
    vi.mocked(portfolioApi.sellPosition).mockRejectedValue(new ApiError('Forbidden', 403))
    renderRow()
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf' }))
    fireEvent.change(screen.getByLabelText('Verkaufsmenge'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Teilverkauf bestätigen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Keine Berechtigung')
  })
})
