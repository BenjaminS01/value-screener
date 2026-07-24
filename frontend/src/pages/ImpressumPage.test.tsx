import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ImpressumPage } from './ImpressumPage'

describe('ImpressumPage', () => {
  it('renders the Impressum heading', () => {
    render(<ImpressumPage />)

    expect(screen.getByRole('heading', { name: 'Impressum' })).toBeInTheDocument()
  })
})
