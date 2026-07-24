import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Footer } from './Footer'

describe('Footer', () => {
  it('shows the mandatory disclaimer text', () => {
    render(<Footer />)

    const footer = screen.getByRole('contentinfo')
    expect(footer.textContent).toContain('keine Anlageberatung oder Anlageempfehlung dar')
  })
})
