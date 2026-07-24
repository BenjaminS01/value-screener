import { useEffect, useState } from 'react'
import { fetchPublicPositions, PublicPosition } from '../api/portfolioApi'

export function PortfolioPage() {
  const [positions, setPositions] = useState<PublicPosition[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchPublicPositions()
      .then(setPositions)
      .catch((err: Error) => setError(err.message))
  }, [])

  if (error) {
    return <p role="alert">{error}</p>
  }

  return (
    <section>
      <h1>Mein Portfolio</h1>
      <ul>
        {positions.map((position) => (
          <li key={position.ticker}>{position.companyName} ({position.ticker})</li>
        ))}
      </ul>
    </section>
  )
}
