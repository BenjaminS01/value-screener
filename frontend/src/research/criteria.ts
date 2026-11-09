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
  PE_RATIO: 'KGV',
  PB_RATIO: 'KBV',
  FIVE_YEAR_AVERAGE_PE: 'KGV-Durchschnitt 5 Jahre',
  FIVE_YEAR_AVERAGE_PB: 'KBV-Durchschnitt 5 Jahre',
  ROE: 'Eigenkapitalrendite',
  DEBT_TO_EQUITY: 'Verschuldungsgrad',
  CURRENT_RATIO: 'Liquidität 3. Grades (Current Ratio)',
  CURRENT_YEAR_NET_MARGIN: 'Nettomarge (aktuelles Jahr)',
  CURRENT_YEAR_FCF_POSITIVE: 'Free Cashflow positiv (aktuelles Jahr)',
  CURRENT_YEAR_NET_INCOME_GREW: 'Gewinn gestiegen (aktuelles Jahr)',
  INSIDER_OWNERSHIP_SHARE: 'Insider-Anteil',
  MARGIN_TREND: 'Margenentwicklung',
  FREE_CASH_FLOW_TREND: 'Free-Cashflow-Entwicklung',
  PROFIT_STABILITY: 'Gewinnstabilität',
  INTEREST_COVERAGE: 'Zinsdeckung',
  MOAT_ASSESSMENT: 'Burggraben (Moat)',
  MANAGEMENT_QUALITY: 'Managementqualität',
  VALUE_TRAP_ASSESSMENT: 'Value-Trap-Einschätzung',
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
