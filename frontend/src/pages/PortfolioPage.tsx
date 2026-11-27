import { useCallback, useEffect, useState } from 'react'
import { writeErrorText } from '../api/errorText'
import { AuthUser } from '../api/authApi'
import { AdminPosition, fetchPortfolio, Position } from '../api/portfolioApi'
import { fetchAllSnapshots } from '../api/researchApi'
import { AddPositionForm } from '../components/AddPositionForm'
import { AdminPositionRow } from '../components/AdminPositionRow'
import { Navigate } from '../navigation'

interface PortfolioPageProps {
  user: AuthUser
  navigate: Navigate
}

export function PortfolioPage({ user, navigate }: PortfolioPageProps) {
  const [positions, setPositions] = useState<Position[]>([])
  const [researchedIsins, setResearchedIsins] = useState<Set<string>>(new Set())
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [writeMessage, setWriteMessage] = useState<string | null>(null)
  const isAdmin = user.role === 'ADMIN'

  const loadPositions = useCallback(() => {
    fetchPortfolio()
      .then((rows) => {
        setError(null)
        setPositions(rows)
      })
      .catch((err: unknown) => setError(writeErrorText(err)))
  }, [])

  useEffect(() => {
    loadPositions()
    fetchAllSnapshots()
      .then((snapshots) => setResearchedIsins(new Set(snapshots.map((s) => s.isin))))
      .catch(() => setResearchedIsins(new Set()))
  }, [loadPositions])

  function handleChanged(conflictMessage?: string) {
    setWriteMessage(conflictMessage ?? null)
    loadPositions()
  }

  function openPosition(isin: string) {
    if (researchedIsins.has(isin)) {
      setNotice(null)
      navigate({ name: 'researchDetail', isin })
    } else {
      setNotice('Not researched yet')
    }
  }

  return (
    <section>
      <h1>Portfolio</h1>
      {notice && <p>{notice}</p>}
      {error && <p role="alert">{error}</p>}
      {writeMessage && <p role="alert">{writeMessage}</p>}
      <h2>Holdings</h2>
      <table>
        <thead>
          <tr>
            <th>Company</th>
            <th>Ticker</th>
            <th>Group</th>
            {isAdmin && (
              <>
                <th>Quantity</th>
                <th>Entry price</th>
                <th>Purchase date</th>
                <th>Actions</th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {positions.map((position) =>
            isAdmin ? (
              <AdminPositionRow
                key={position.isin}
                position={position as AdminPosition}
                onOpen={openPosition}
                onChanged={handleChanged}
              />
            ) : (
              <tr key={position.isin}>
                <td>
                  <button type="button" onClick={() => openPosition(position.isin)}>
                    {position.companyName}
                  </button>
                </td>
                <td>{position.ticker}</td>
                <td>{position.group ? position.group.name : '—'}</td>
              </tr>
            ),
          )}
        </tbody>
      </table>
      {isAdmin && (
        <>
          <h2>Buy position</h2>
          <AddPositionForm onAdded={handleChanged} />
        </>
      )}
    </section>
  )
}
