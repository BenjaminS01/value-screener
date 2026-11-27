import { useEffect, useState } from 'react'
import { fetchPortfolio } from '../api/portfolioApi'
import {
  assignGroup,
  CompanySnapshot,
  Finding,
  FindingHistoryEntry,
  fetchGroups,
  fetchHistory,
  fetchSnapshot,
  StockGroup,
} from '../api/researchApi'
import type { AuthUser } from '../api/authApi'
import { writeErrorText } from '../api/errorText'
import type { Navigate } from '../navigation'
import { InvestmentThesisSection } from '../components/InvestmentThesisSection'
import { FindingHistory } from '../components/FindingHistory'
import { formatDate, formatValue } from '../research/format'
import { DeepAnalysisSection } from '../components/DeepAnalysisSection'
import { CRITERION_LABELS, FUNDAMENTAL_CRITERIA, isSafeHttpUrl, QUALITATIVE_CRITERIA } from '../research/criteria'

interface Props {
  user: AuthUser
  isin: string
  navigate: Navigate
}

const PLACEHOLDER = 'Coming in a later phase'

function CriteriaList({
  keys,
  findings,
  history,
}: {
  keys: readonly string[]
  findings: Finding[]
  history: FindingHistoryEntry[]
}) {
  return (
    <dl className="criteria">
      {keys.map((key) => {
        const finding = findings.find((f) => f.criterionKey === key)
        return (
          <div key={key} className="criteria__row">
            <dt>{CRITERION_LABELS[key]}</dt>
            <dd>
              {finding === undefined ? (
                <span className="badge badge--muted">Not researched</span>
              ) : (
                <>
                  {formatValue(finding) !== null && <strong className="value">{formatValue(finding)}</strong>}{' '}
                  <span>{finding.claim}</span> <span className="date">{formatDate(finding.asOfDate)}</span>{' '}
                  {finding.stale && <span className="badge badge--stale">Stale</span>}{' '}
                  {isSafeHttpUrl(finding.sourceUrl) ? (
                    <a href={finding.sourceUrl} target="_blank" rel="noopener noreferrer">
                      Source
                    </a>
                  ) : (
                    <span>{finding.sourceUrl}</span>
                  )}
                </>
              )}
              <FindingHistory entries={history.filter((h) => h.criterionKey === key)} />
            </dd>
          </div>
        )
      })}
    </dl>
  )
}

export function ResearchDetailPage({ user, isin, navigate }: Props) {
  const [snapshot, setSnapshot] = useState<CompanySnapshot | null>(null)
  const [groups, setGroups] = useState<StockGroup[]>([])
  const [findingHistory, setFindingHistory] = useState<FindingHistoryEntry[]>([])
  const [historyError, setHistoryError] = useState(false)
  const [held, setHeld] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedGroup, setSelectedGroup] = useState('')
  const [editingGroup, setEditingGroup] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // The badge is optional: a failing portfolio call must not block the page.
    const portfolio = fetchPortfolio().then(
      (rows) => rows.some((r) => r.isin === isin),
      () => false,
    )
    Promise.all([fetchSnapshot(isin), fetchGroups(), portfolio])
      .then(([s, g, h]) => {
        if (cancelled) return
        setSnapshot(s)
        setGroups(g)
        setHeld(h)
        setSelectedGroup(s?.group?.key ?? g[0]?.key ?? '')
        if (s === null) return
        // History is optional: a failing call must not block the page.
        fetchHistory(isin).then(
          (hist) => {
            if (cancelled) return
            setFindingHistory(hist.findings)
          },
          () => {
            if (!cancelled) setHistoryError(true)
          },
        )
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
  }, [isin])

  function editGroup() {
    setSelectedGroup(snapshot?.group?.key ?? groups[0]?.key ?? '')
    setSaveError(null)
    setEditingGroup(true)
  }

  async function saveGroup() {
    setSaveError(null)
    // Unchanged group: nothing to save, just leave edit mode.
    if (selectedGroup === snapshot?.group?.key) {
      setEditingGroup(false)
      return
    }
    setSaving(true)
    try {
      const updated = await assignGroup(isin, selectedGroup)
      setSnapshot(updated)
      setSelectedGroup(updated.group?.key ?? selectedGroup)
      setEditingGroup(false)
    } catch (e) {
      setSaveError(writeErrorText(e))
    } finally {
      setSaving(false)
    }
  }

  const back = (
    <button type="button" className="btn-link" onClick={() => navigate({ name: 'research' })}>
      <span aria-hidden="true">← </span>Back to overview
    </button>
  )

  if (loading) return <p className="muted">Loading …</p>
  if (error !== null) {
    return (
      <section>
        {back}
        <p role="alert">{error}</p>
      </section>
    )
  }
  if (snapshot === null) {
    return (
      <section>
        {back}
        <p>No research for this stock yet</p>
      </section>
    )
  }

  return (
    <section>
      {back}
      <h1>{snapshot.companyName}</h1>
      <p className="muted">
        {snapshot.lastResearchedAt === null
          ? 'No findings yet'
          : `Last researched ${formatDate(snapshot.lastResearchedAt)}${snapshot.stale ? ' – consider re-research' : ''}`}
      </p>
      {historyError && <p className="notice">History could not be loaded</p>}
      {held && (
        <p>
          <span className="badge badge--portfolio">The operator holds this position</span>
        </p>
      )}

      <div className="card">
        <h2>A. Overview</h2>
        <p>
          Ticker: <span>{snapshot.ticker}</span>
        </p>
        <p>
          ISIN: <span>{snapshot.isin}</span>
        </p>
        <p>
          Country: <span>{snapshot.country?.trim() || '–'}</span>
        </p>
        <p>
          Description: <span>{snapshot.businessDescription?.trim() || '–'}</span>
        </p>
        <div>
          {editingGroup ? (
            <>
              <label>
                Group:{' '}
                <select value={selectedGroup} onChange={(e) => setSelectedGroup(e.target.value)}>
                  {groups.map((g) => (
                    <option key={g.key} value={g.key}>
                      {g.name}
                    </option>
                  ))}
                </select>
              </label>{' '}
              <button type="button" onClick={saveGroup} disabled={saving || selectedGroup === ''}>
                Save
              </button>
            </>
          ) : (
            <>
              Group:{' '}
              {snapshot.group === null ? (
                <span>–</span>
              ) : (
                <span>
                  {snapshot.group.name} ({snapshot.group.source === 'AI' ? 'AI suggestion' : 'manual'})
                </span>
              )}
              {user.role === 'ADMIN' && (
                <>
                  {' '}
                  <button type="button" onClick={editGroup}>
                    Edit
                  </button>
                </>
              )}
            </>
          )}
          {saveError !== null && <p role="alert">{saveError}</p>}
        </div>
      </div>

      <InvestmentThesisSection user={user} isin={isin} />

      <div className="card">
        <h2>B. Key figures</h2>
        <CriteriaList keys={FUNDAMENTAL_CRITERIA} findings={snapshot.findings} history={findingHistory} />
      </div>

      <div className="card">
        <h2>C. Qualitative findings</h2>
        <CriteriaList keys={QUALITATIVE_CRITERIA} findings={snapshot.findings} history={findingHistory} />
      </div>

      <div className="card">
        <h2>D. Quarterly reports</h2>
        <button type="button" disabled title="Not available yet">
          Upload
        </button>
        <p className="muted">{PLACEHOLDER}</p>
      </div>

      <div className="card">
        <h2>E. AI chat</h2>
        <button type="button" disabled>
          Summarize quarterly reports
        </button>
        <input disabled aria-label="Question about the stock" />
        <p className="muted">{PLACEHOLDER}</p>
      </div>

      <div className="card">
        <h2>F. Deep analysis</h2>
        <DeepAnalysisSection key={isin} isin={isin} />
      </div>
    </section>
  )
}
