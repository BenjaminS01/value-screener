import { useEffect, useState } from 'react'
import { fetchPublicPositions, PublicPosition } from '../api/portfolioApi'
import { CompanySnapshot, fetchAllSnapshots } from '../api/researchApi'
import { DeepResearchPlaceholder } from '../components/DeepResearchPlaceholder'
import { PositionResearch } from '../components/PositionResearch'

export function ResearchLibraryPage() {
  const [snapshots, setSnapshots] = useState<CompanySnapshot[]>([])
  const [positions, setPositions] = useState<PublicPosition[]>([])
  const [error, setError] = useState<string | null>(null)
  const [expandedIsins, setExpandedIsins] = useState<Set<string>>(new Set())
  const [selectedTicker, setSelectedTicker] = useState('')

  useEffect(() => {
    Promise.all([fetchAllSnapshots(), fetchPublicPositions()])
      .then(([snapshotResults, positionResults]) => {
        setSnapshots(snapshotResults)
        setPositions(positionResults)
      })
      .catch((err: Error) => setError(err.message))
  }, [])

  function toggleExpanded(isin: string) {
    setExpandedIsins((previous) => {
      const next = new Set(previous)
      if (next.has(isin)) {
        next.delete(isin)
      } else {
        next.add(isin)
      }
      return next
    })
  }

  if (error) {
    return <p role="alert">{error}</p>
  }

  const ownedTickers = new Set(positions.map((position) => position.ticker))
  const knownTickers = Array.from(
    new Set([...positions.map((position) => position.ticker), ...snapshots.map((snapshot) => snapshot.ticker)]),
  ).sort()

  return (
    <section>
      <h1>Research</h1>
      <ul>
        {snapshots.map((snapshot) => {
          const isExpanded = expandedIsins.has(snapshot.isin)
          const isOwned = ownedTickers.has(snapshot.ticker)
          return (
            <li key={snapshot.isin}>
              <button type="button" onClick={() => toggleExpanded(snapshot.isin)}>
                {isExpanded ? '▾' : '▸'} {snapshot.companyName} ({snapshot.ticker})
              </button>
              {isOwned && <p>Note: the operator holds this position in their own portfolio.</p>}
              {isExpanded && <PositionResearch isin={snapshot.isin} />}
            </li>
          )
        })}
      </ul>
      <div>
        <label>
          Select a stock
          <select value={selectedTicker} onChange={(e) => setSelectedTicker(e.target.value)}>
            <option value="">-- choose a ticker --</option>
            {knownTickers.map((ticker) => (
              <option key={ticker} value={ticker}>
                {ticker}
              </option>
            ))}
          </select>
        </label>
        {selectedTicker && <DeepResearchPlaceholder />}
      </div>
    </section>
  )
}
