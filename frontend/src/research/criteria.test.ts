import { describe, expect, it } from 'vitest'
import { CRITERION_LABELS, FUNDAMENTAL_CRITERIA, isSafeHttpUrl, QUALITATIVE_CRITERIA } from './criteria'

const ALL = [
  'PE_RATIO', 'PB_RATIO', 'FIVE_YEAR_AVERAGE_PE', 'FIVE_YEAR_AVERAGE_PB', 'ROE', 'DEBT_TO_EQUITY',
  'CURRENT_RATIO', 'CURRENT_YEAR_NET_MARGIN', 'CURRENT_YEAR_FCF_POSITIVE', 'CURRENT_YEAR_NET_INCOME_GREW',
  'INSIDER_OWNERSHIP_SHARE', 'MARGIN_TREND', 'FREE_CASH_FLOW_TREND', 'PROFIT_STABILITY',
  'INTEREST_COVERAGE', 'MOAT_ASSESSMENT', 'MANAGEMENT_QUALITY', 'VALUE_TRAP_ASSESSMENT',
]

describe('criteria', () => {
  it('covers all 18 criteria exactly once', () => {
    const joined = [...FUNDAMENTAL_CRITERIA, ...QUALITATIVE_CRITERIA]
    expect(joined).toHaveLength(18)
    expect([...joined].sort()).toEqual([...ALL].sort())
    for (const k of joined) expect(CRITERION_LABELS[k]).toBeTruthy()
  })

  it('accepts only http(s) urls', () => {
    expect(isSafeHttpUrl('https://x.de')).toBe(true)
    expect(isSafeHttpUrl('http://x.de')).toBe(true)
    for (const bad of ['javascript:alert(1)', 'ftp://x', '', 'not a url']) expect(isSafeHttpUrl(bad)).toBe(false)
  })
})
