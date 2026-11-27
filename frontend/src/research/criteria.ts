export const FUNDAMENTAL_CRITERIA: readonly string[] = [
  'PE_RATIO',
  'PB_RATIO',
  'FIVE_YEAR_AVERAGE_PE',
  'FIVE_YEAR_AVERAGE_PB',
  'ROE',
  'DEBT_TO_EQUITY',
  'CURRENT_RATIO',
  'CURRENT_YEAR_NET_MARGIN',
  'CURRENT_YEAR_FCF_POSITIVE',
  'CURRENT_YEAR_NET_INCOME_GREW',
  'INSIDER_OWNERSHIP_SHARE',
]

export const QUALITATIVE_CRITERIA: readonly string[] = [
  'MARGIN_TREND',
  'FREE_CASH_FLOW_TREND',
  'PROFIT_STABILITY',
  'INTEREST_COVERAGE',
  'MOAT_ASSESSMENT',
  'MANAGEMENT_QUALITY',
  'VALUE_TRAP_ASSESSMENT',
]

export const CRITERION_LABELS: Record<string, string> = {
  PE_RATIO: 'P/E ratio',
  PB_RATIO: 'P/B ratio',
  FIVE_YEAR_AVERAGE_PE: '5-year average P/E',
  FIVE_YEAR_AVERAGE_PB: '5-year average P/B',
  ROE: 'Return on equity',
  DEBT_TO_EQUITY: 'Debt to equity',
  CURRENT_RATIO: 'Current ratio',
  CURRENT_YEAR_NET_MARGIN: 'Net margin (current year)',
  CURRENT_YEAR_FCF_POSITIVE: 'Free cash flow positive (current year)',
  CURRENT_YEAR_NET_INCOME_GREW: 'Net income grew (current year)',
  INSIDER_OWNERSHIP_SHARE: 'Insider ownership',
  MARGIN_TREND: 'Margin trend',
  FREE_CASH_FLOW_TREND: 'Free cash flow trend',
  PROFIT_STABILITY: 'Profit stability',
  INTEREST_COVERAGE: 'Interest coverage',
  MOAT_ASSESSMENT: 'Moat',
  MANAGEMENT_QUALITY: 'Management quality',
  VALUE_TRAP_ASSESSMENT: 'Value trap assessment',
}

/** True only for absolute http: / https: URLs; guards against javascript: and similar schemes. */
export function isSafeHttpUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}
