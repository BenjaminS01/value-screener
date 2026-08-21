import { useEffect, useState } from 'react'
import { CompanySnapshot, fetchSnapshot } from '../api/researchApi'

interface PositionResearchProps {
  isin: string
}

const CRITERION_LABELS: Record<string, string> = {
  PE_RATIO: 'P/E Ratio',
  PB_RATIO: 'P/B Ratio',
  FIVE_YEAR_AVERAGE_PE: '5-Year Average P/E',
  FIVE_YEAR_AVERAGE_PB: '5-Year Average P/B',
  ROE: 'Return on Equity',
  DEBT_TO_EQUITY: 'Debt-to-Equity',
  CURRENT_RATIO: 'Current Ratio',
  CURRENT_YEAR_NET_MARGIN: 'Current-Year Net Margin',
  CURRENT_YEAR_FCF_POSITIVE: 'Current-Year FCF Positive',
  CURRENT_YEAR_NET_INCOME_GREW: 'Current-Year Net Income Growth',
  INSIDER_OWNERSHIP_SHARE: 'Insider Ownership Share',
  MARGIN_TREND: 'Margin Trend',
  FREE_CASH_FLOW_TREND: 'Free Cash Flow Trend',
  PROFIT_STABILITY: 'Profit Stability',
  INTEREST_COVERAGE: 'Interest Coverage',
  MOAT_ASSESSMENT: 'Moat Assessment',
  MANAGEMENT_QUALITY: 'Management Quality',
  VALUE_TRAP_ASSESSMENT: 'Value Trap Assessment',
}

export function PositionResearch({ isin }: PositionResearchProps) {
  const [snapshot, setSnapshot] = useState<CompanySnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setError(null)
    setSnapshot(null)
    fetchSnapshot(isin)
      .then(setSnapshot)
      .catch((err: Error) => setError(err.message))
  }, [isin])

  if (error) {
    return <p role="alert">{error}</p>
  }

  if (!snapshot) {
    return <p>Not yet researched.</p>
  }

  return (
    <div>
      <header>
        <p>{snapshot.sector}</p>
        <p>{snapshot.country}</p>
        <p>{snapshot.businessDescription}</p>
      </header>
      <ul>
        {snapshot.findings.map((finding) => (
          <li key={finding.criterionKey}>
            <strong>{CRITERION_LABELS[finding.criterionKey] ?? finding.criterionKey}</strong>
            <p>{finding.claim}</p>
          </li>
        ))}
      </ul>
    </div>
  )
}
