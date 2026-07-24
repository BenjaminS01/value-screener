import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AddPositionForm } from './AddPositionForm'

describe('AddPositionForm', () => {
  const credentials = { username: 'admin', password: 'secret' }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('submits the entered position with a basic auth header', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true })
    const onAdded = vi.fn()

    render(<AddPositionForm credentials={credentials} onAdded={onAdded} />)

    fireEvent.change(screen.getByLabelText('Ticker'), { target: { value: 'aapl' } })
    fireEvent.change(screen.getByLabelText('ISIN'), { target: { value: 'US0378331005' } })
    fireEvent.change(screen.getByLabelText('Unternehmensname'), { target: { value: 'Apple Inc.' } })
    fireEvent.change(screen.getByLabelText('Stückzahl'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Einstiegspreis'), { target: { value: '150' } })
    fireEvent.change(screen.getByLabelText('Kaufdatum'), { target: { value: '2026-01-15' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    await waitFor(() => expect(onAdded).toHaveBeenCalled())

    const [, options] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(options.headers.Authorization).toBe('Basic ' + btoa('admin:secret'))
  })
})
