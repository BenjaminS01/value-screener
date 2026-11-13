import { describe, expect, it } from 'vitest'
import { formatDateTime } from './format'

describe('formatDateTime', () => {
  it('formats date and time in de-DE, UTC', () => {
    expect(formatDateTime('2026-11-12T13:45:30Z')).toBe('12.11.2026, 13:45')
    expect(formatDateTime('2026-11-12T23:05:00+02:00')).toBe('12.11.2026, 21:05')
  })
  it('returns the input when unparseable', () => {
    expect(formatDateTime('nope')).toBe('nope')
  })
})
