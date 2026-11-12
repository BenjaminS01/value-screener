import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Footer } from './Footer'

describe('Footer', () => {
  it('shows the mandatory disclaimer text', () => {
    render(<Footer navigate={vi.fn()} />)

    const footer = screen.getByRole('contentinfo')
    expect(footer.textContent).toContain('keine Anlageberatung oder Anlageempfehlung dar')
  })

  it('navigates to the legal pages', () => {
    const navigate = vi.fn()
    render(<Footer navigate={navigate} />)
    fireEvent.click(screen.getByRole('button', { name: 'Impressum' }))
    expect(navigate).toHaveBeenCalledWith({ name: 'impressum' })
    fireEvent.click(screen.getByRole('button', { name: 'Datenschutz' }))
    expect(navigate).toHaveBeenCalledWith({ name: 'datenschutz' })
  })
})
