import { useEffect, useState } from 'react'
import { fetchPortfolio } from '../api/portfolioApi'
import { fetchAllSnapshots, fetchGroups, SnapshotSummary, StockGroup } from '../api/researchApi'
import type { AuthUser } from '../api/authApi'
import { writeErrorText } from '../api/errorText'
import type { Navigate } from '../navigation'
import { groupByCountry, groupByStockGroup, matchesSearch } from '../research/grouping'

interface Props {
  user: AuthUser
  navigate: Navigate
}

type Grouping = 'group' | 'country'

export function ResearchOverviewPage({ navigate }: Props) {
  const [snapshots, setSnapshots] = useState<SnapshotSummary[]>([])
  const [groups, setGroups] = useState<StockGroup[]>([])
  const [held, setHeld] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [grouping, setGrouping] = useState<Grouping>('group')
  const [query, setQuery] = useState('')

  useEffect(() => {
    let cancelled = false
    // The portfolio only adds markers; its failure must not block the page.
    const portfolio = fetchPortfolio().then(
      (rows) => new Set(rows.map((r) => r.isin)),
      () => new Set<string>(),
    )
    Promise.all([fetchAllSnapshots(), fetchGroups(), portfolio])
      .then(([s, g, h]) => {
        if (cancelled) return
        setSnapshots(s)
        setGroups(g)
        setHeld(h)
      })
      .catch((e) => {
        if (!cancelled) setError(writeErrorText(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const visible = snapshots.filter((s) => matchesSearch(s, query))
  const sections = grouping === 'group' ? groupByStockGroup(visible, groups) : groupByCountry(visible)

  return (
    <section>
      <h1>Research</h1>
      <label>
        Search
        <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} />
      </label>
      <div role="radiogroup" aria-label="Grouping">
        <label>
          <input type="radio" name="grouping" checked={grouping === 'group'} onChange={() => setGrouping('group')} />
          by group
        </label>
        <label>
          <input type="radio" name="grouping" checked={grouping === 'country'} onChange={() => setGrouping('country')} />
          by country
        </label>
      </div>
      {loading && <p>Loading …</p>}
      {error !== null && <p role="alert">{error}</p>}
      {sections.map((section) => (
        <div key={section.title}>
          <h2>{section.title}</h2>
          <ul>
            {section.snapshots.map((s) => (
              <li key={s.isin}>
                <button type="button" onClick={() => navigate({ name: 'researchDetail', isin: s.isin })}>
                  <span>{s.companyName}</span> <span>{s.ticker}</span> <span>{s.isin}</span>{' '}
                  <span>{grouping === 'country' ? (s.group?.name ?? '–') : (s.country?.trim() || '–')}</span>
                  {s.stale && <> <span>Stale</span></>}
                  {held.has(s.isin) && <> <span>In portfolio</span></>}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  )
}
