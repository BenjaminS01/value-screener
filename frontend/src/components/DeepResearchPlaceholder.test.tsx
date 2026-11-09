import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DeepResearchPlaceholder } from './DeepResearchPlaceholder'

describe('DeepResearchPlaceholder', () => {
  it('shows a disabled start button and the later-phase note', () => {
    render(<DeepResearchPlaceholder />)
    expect(screen.getByRole('button', { name: 'Tiefenanalyse starten' })).toBeDisabled()
    expect(screen.getByText('Folgt in einer späteren Phase')).toBeInTheDocument()
  })
})
