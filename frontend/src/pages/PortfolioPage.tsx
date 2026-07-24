import { useEffect, useState } from 'react'
import { Credentials, fetchPublicPositions, PublicPosition } from '../api/portfolioApi'
import { AddPositionForm } from '../components/AddPositionForm'
import { LoginForm } from '../components/LoginForm'

export function PortfolioPage() {
  const [positions, setPositions] = useState<PublicPosition[]>([])
  const [error, setError] = useState<string | null>(null)
  const [credentials, setCredentials] = useState<Credentials | null>(null)

  function loadPositions() {
    fetchPublicPositions()
      .then(setPositions)
      .catch((err: Error) => setError(err.message))
  }

  useEffect(() => {
    loadPositions()
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
      {credentials ? (
        <AddPositionForm credentials={credentials} onAdded={loadPositions} />
      ) : (
        <LoginForm onLogin={setCredentials} />
      )}
    </section>
  )
}
