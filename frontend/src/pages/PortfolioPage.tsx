import { useEffect, useState } from 'react'
import { Credentials, fetchPublicPositions, PublicPosition } from '../api/portfolioApi'
import { AddPositionForm } from '../components/AddPositionForm'
import { DeepResearchPlaceholder } from '../components/DeepResearchPlaceholder'
import { LoginForm } from '../components/LoginForm'
import { PositionResearch } from '../components/PositionResearch'

export function PortfolioPage() {
  const [positions, setPositions] = useState<PublicPosition[]>([])
  const [error, setError] = useState<string | null>(null)
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  const [expandedIsins, setExpandedIsins] = useState<Set<string>>(new Set())

  function loadPositions() {
    fetchPublicPositions()
      .then(setPositions)
      .catch((err: Error) => setError(err.message))
  }

  useEffect(() => {
    loadPositions()
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

  return (
    <section>
      <h1>My Portfolio</h1>
      <ul>
        {positions.map((position) => {
          const isExpanded = expandedIsins.has(position.isin)
          return (
            <li key={position.isin}>
              <button type="button" onClick={() => toggleExpanded(position.isin)}>
                {isExpanded ? '▾' : '▸'} {position.companyName} ({position.ticker})
              </button>
              {isExpanded && (
                <div>
                  <PositionResearch isin={position.isin} />
                  <DeepResearchPlaceholder />
                </div>
              )}
            </li>
          )
        })}
      </ul>
      {credentials ? (
        <AddPositionForm credentials={credentials} onAdded={loadPositions} />
      ) : (
        <LoginForm onLogin={setCredentials} />
      )}
    </section>
  )
}
