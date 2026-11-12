import { describe, expect, it } from 'vitest'
import type { CompanySnapshot, StockGroup } from '../api/researchApi'
import { groupByCountry, groupByStockGroup, matchesSearch } from './grouping'

function snap(over: Partial<CompanySnapshot>): CompanySnapshot {
  return {
    id: 1, ticker: 'T', isin: 'I', companyName: 'N', country: 'US',
    businessDescription: '', group: null, findings: [], lastResearchedAt: null, stale: false, ...over,
  }
}
const g = (key: string, name: string) => ({ key, name, source: 'AI' as const })
const groups: StockGroup[] = [
  { key: 'tech', name: 'Technologie', sortOrder: 1, stockCount: 2 },
  { key: 'health', name: 'Gesundheit', sortOrder: 2, stockCount: 0 },
  { key: 'energy', name: 'Energie', sortOrder: 3, stockCount: 1 },
]

describe('groupByStockGroup', () => {
  it('orders by catalog, skips empty groups, puts ungrouped last, sorts by name', () => {
    const s = [
      snap({ isin: '1', companyName: 'Zeta', group: g('tech', 'Technologie') }),
      snap({ isin: '2', companyName: 'Alpha', group: g('tech', 'Technologie') }),
      snap({ isin: '3', companyName: 'Oel', group: g('energy', 'Energie') }),
      snap({ isin: '4', companyName: 'Nix', group: null }),
    ]
    const r = groupByStockGroup(s, groups)
    expect(r.map((x) => x.title)).toEqual(['Technologie', 'Energie', 'Ohne Gruppe'])
    expect(r[0].snapshots.map((x) => x.companyName)).toEqual(['Alpha', 'Zeta'])
  })
  it('puts a snapshot with an unknown group key into Ohne Gruppe', () => {
    const r = groupByStockGroup([snap({ group: g('zzz', 'Zzz') })], groups)
    expect(r.map((x) => x.title)).toEqual(['Ohne Gruppe'])
  })
})

describe('groupByCountry', () => {
  it('sorts alphabetically; null/empty/blank country goes last', () => {
    const s = [
      snap({ isin: '1', country: 'US' }),
      snap({ isin: '2', country: null }),
      snap({ isin: '3', country: 'DE' }),
      snap({ isin: '4', country: '' }),
      snap({ isin: '5', country: '  ' }),
    ]
    const r = groupByCountry(s)
    expect(r.map((x) => x.title)).toEqual(['DE', 'US', 'Ohne Land'])
    expect(r[2].snapshots).toHaveLength(3)
  })
})

describe('matchesSearch', () => {
  const s = snap({ companyName: 'Apple Inc.', ticker: 'AAPL', isin: 'US0378331005' })
  it('matches name, ticker, isin case-insensitively', () => {
    expect(matchesSearch(s, 'apple')).toBe(true)
    expect(matchesSearch(s, 'aapl')).toBe(true)
    expect(matchesSearch(s, 'us03783')).toBe(true)
    expect(matchesSearch(s, 'xyz')).toBe(false)
  })
  it('empty query matches all', () => {
    expect(matchesSearch(s, '')).toBe(true)
    expect(matchesSearch(s, '  ')).toBe(true)
  })
})
