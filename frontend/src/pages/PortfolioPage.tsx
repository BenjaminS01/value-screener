import { useCallback, useEffect, useState } from 'react'
import { writeErrorText } from '../api/errorText'
import { AuthUser } from '../api/authApi'
import { AdminPosition, fetchPortfolio, Position } from '../api/portfolioApi'
import { fetchAllSnapshots } from '../api/researchApi'
import { AddPositionForm } from '../components/AddPositionForm'
import { PositionActions } from '../components/PositionActions'
import { Navigate } from '../navigation'

interface PortfolioPageProps {
  user: AuthUser
  navigate: Navigate
}

function formatNumber(value: number, fractionDigits?: number): string {
  return value.toLocaleString('de-DE', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits ?? 20,
  })
}

function formatDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${day}.${month}.${year}`
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
          {positions.map((position) => {
            const admin = isAdmin ? (position as AdminPosition) : null
            return (
              <tr key={position.isin}>
                <td>
                  <button type="button" onClick={() => openPosition(position.isin)}>
                    {position.companyName}
                  </button>
                </td>
                <td>{position.ticker}</td>
                <td>{position.group ? position.group.name : '—'}</td>
                {admin && (
                  <>
                    <td>{formatNumber(admin.quantity)}</td>
                    <td>{formatNumber(admin.entryPrice, 2)}</td>
                    <td>{formatDate(admin.purchaseDate)}</td>
                    <td>
                      <PositionActions position={admin} onChanged={handleChanged} />
                    </td>
                  </>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
      {isAdmin && <AddPositionForm onAdded={handleChanged} />}
    </section>
  )
}
