import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DeepResearchPlaceholder } from './DeepResearchPlaceholder'

describe('DeepResearchPlaceholder', () => {
  it('hides the explanation until the button is clicked', () => {
    render(<DeepResearchPlaceholder />)

    expect(screen.queryByText(/LLM API/)).not.toBeInTheDocument()
  })

  it('shows an explanation mentioning future LLM and trading API integration on click', () => {
    render(<DeepResearchPlaceholder />)

    fireEvent.click(screen.getByRole('button', { name: 'Run deep research' }))

    expect(
      screen.getByText(/potentially via an LLM API or a trading API/),
    ).toBeInTheDocument()
  })
})
