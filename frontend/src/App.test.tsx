import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => [] }),
    )
  })

  it('renders the app title and portfolio view by default', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Value Screener' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Mein Portfolio' })).toBeInTheDocument()
  })

  it('switches to the Impressum view', async () => {
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Impressum' }))
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Impressum' })).toBeInTheDocument()
    })
  })
})
