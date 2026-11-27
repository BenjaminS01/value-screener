import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ProjectOverview } from './ProjectOverview'

describe('ProjectOverview', () => {
  it('shows the title and the three tiles', () => {
    render(<ProjectOverview />)
    expect(screen.getByRole('heading', { level: 1, name: 'Value Screener' })).toBeInTheDocument()
    for (const name of ['Portfolio', 'Research', 'Tech']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }
  })

  it('links to the source code on GitHub in a new tab', () => {
    render(<ProjectOverview />)
    const link = screen.getByRole('link', { name: 'Source code on GitHub' })
    expect(link).toHaveAttribute('href', 'https://github.com/BenjaminS01/value-screener')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })
})
