import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LandingPage } from './LandingPage'

describe('LandingPage', () => {
  it('renders a title heading', () => {
    render(<LandingPage />)

    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('explains how research works today', () => {
    render(<LandingPage />)

    expect(screen.getByRole('heading', { name: 'How it works' })).toBeInTheDocument()
    expect(screen.getByText(/research is done manually: the operator runs/i)).toBeInTheDocument()
  })

  it('describes the architecture and is honest that AWS deployment is not yet live', () => {
    render(<LandingPage />)

    expect(screen.getByRole('heading', { name: 'Architecture' })).toBeInTheDocument()
    expect(screen.getByText(/Java 21/)).toBeInTheDocument()
    expect(screen.getByText(/Spring Boot 3/)).toBeInTheDocument()
    expect(screen.getByText(/React/)).toBeInTheDocument()
    expect(screen.getByText(/PostgreSQL/)).toBeInTheDocument()
    expect(screen.getByText(/planned, but not yet live/i)).toBeInTheDocument()
  })

  it('mentions the roadmap for automated deep research', () => {
    render(<LandingPage />)

    expect(screen.getByRole('heading', { name: 'Roadmap' })).toBeInTheDocument()
    expect(screen.getByText(/LLM/)).toBeInTheDocument()
    expect(screen.getByText(/trading/i)).toBeInTheDocument()
  })
})
