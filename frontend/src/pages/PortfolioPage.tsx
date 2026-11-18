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
      setNotice('Noch nicht recherchiert')
    }
  }

  return (
    <section>
      <h1>Portfolio</h1>
      {notice && <p>{notice}</p>}
      {error && <p role="alert">{error}</p>}
      {writeMessage && <p role="alert">{writeMessage}</p>}
      <table>
        <thead>
          <tr>
            <th>Unternehmen</th>
            <th>Ticker</th>
            <th>Gruppe</th>
            {isAdmin && (
              <>
                <th>Stückzahl</th>
                <th>Einstandskurs</th>
                <th>Kaufdatum</th>
                <th>Aktionen</th>
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
      {isAdmin && <AddPositionForm onAdded={handleChanged} />}
    </section>
  )
}
