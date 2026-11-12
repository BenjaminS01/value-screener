import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FindingHistory } from './FindingHistory'
import type { FindingHistoryEntry } from '../api/researchApi'

function entry(over: Partial<FindingHistoryEntry> = {}): FindingHistoryEntry {
  return {
    criterionKey: 'PE_RATIO', numericValue: 10, booleanValue: null, claim: 'alt', sourceUrl: 'https://example.com/a',
    asOfDate: '2025-12-31', replacedAt: '2026-03-02T10:00:00Z', origin: 'INGEST', ...over,
  }
}

describe('FindingHistory', () => {
  it('renders nothing for an empty list', () => {
    const { container } = render(<FindingHistory entries={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('shows a collapsed toggle with the count and expands on click', () => {
    render(<FindingHistory entries={[entry({ claim: 'neuer' }), entry({ claim: 'aelter', numericValue: null, booleanValue: true })]} />)
    expect(screen.queryByText('neuer')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Verlauf (2)' }))
    expect(screen.getByText('neuer')).toBeInTheDocument()
    expect(screen.getByText('aelter')).toBeInTheDocument()
    expect(screen.getByText('ja')).toBeInTheDocument()
    expect(screen.getAllByText('ersetzt am 02.03.2026')).toHaveLength(2)
  })

  it('keeps the given order and renders unsafe urls as text', () => {
    render(<FindingHistory entries={[entry({ claim: 'first' }), entry({ claim: 'second', sourceUrl: 'javascript:alert(1)' })]} />)
    fireEvent.click(screen.getByRole('button', { name: 'Verlauf (2)' }))
    const items = screen.getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('first')
    expect(items[1]).toHaveTextContent('second')
    expect(screen.getAllByRole('link')).toHaveLength(1)
  })
})
