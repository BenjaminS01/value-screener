import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StartPage } from './StartPage'

describe('StartPage', () => {
  it('shows the project overview', () => {
    render(<StartPage />)
    expect(screen.getByRole('heading', { level: 1, name: 'Value Screener' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Source code on GitHub' })).toBeInTheDocument()
  })
})
