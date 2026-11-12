import { useEffect, useState } from 'react'
import { fetchPortfolio } from '../api/portfolioApi'
import { assignGroup, CompanySnapshot, Finding, fetchGroups, fetchSnapshot, StockGroup } from '../api/researchApi'
import type { AuthUser } from '../api/authApi'
import { writeErrorText } from '../api/errorText'
import type { Navigate } from '../navigation'
import { DeepResearchPlaceholder } from '../components/DeepResearchPlaceholder'
import {
  CRITERION_LABELS,
  FUNDAMENTAL_CRITERIA,
  isSafeHttpUrl,
  QUALITATIVE_CRITERIA,
} from '../research/criteria'

interface Props {
  user: AuthUser
  isin: string
  navigate: Navigate
}

const PLACEHOLDER = 'Folgt in einer späteren Phase'

function formatValue(f: Finding): string | null {
  if (f.numericValue !== null) return f.numericValue.toLocaleString('de-DE', { maximumFractionDigits: 2 })
  if (f.booleanValue !== null) return f.booleanValue ? 'ja' : 'nein'
  return null
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString('de-DE', { timeZone: 'UTC' })
}

function CriteriaList({ keys, findings }: { keys: readonly string[]; findings: Finding[] }) {
  return (
    <dl>
      {keys.map((key) => {
        const finding = findings.find((f) => f.criterionKey === key)
        return (
          <div key={key}>
            <dt>{CRITERION_LABELS[key]}</dt>
            <dd>
              {finding === undefined ? (
                <span>nicht recherchiert</span>
              ) : (
                <>
                  {formatValue(finding) !== null && <strong>{formatValue(finding)}</strong>}{' '}
                  <span>{finding.claim}</span> <span>{formatDate(finding.asOfDate)}</span>{' '}
                  {isSafeHttpUrl(finding.sourceUrl) ? (
                    <a href={finding.sourceUrl} target="_blank" rel="noopener noreferrer">
                      Quelle
                    </a>
                  ) : (
                    <span>{finding.sourceUrl}</span>
                  )}
                </>
              )}
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
  const [held, setHeld] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedGroup, setSelectedGroup] = useState('')
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

  async function saveGroup() {
    setSaving(true)
    setSaveError(null)
    try {
      const updated = await assignGroup(isin, selectedGroup)
      setSnapshot(updated)
      setSelectedGroup(updated.group?.key ?? selectedGroup)
    } catch (e) {
      setSaveError(writeErrorText(e))
    } finally {
      setSaving(false)
    }
  }

  const back = (
    <button type="button" onClick={() => navigate({ name: 'research' })}>
      Zurück zur Übersicht
    </button>
  )

  if (loading) return <p>Lade …</p>
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
        <p>Für diese Aktie gibt es noch keine Recherche</p>
      </section>
    )
  }

  return (
    <section>
      {back}
      <h1>{snapshot.companyName}</h1>
      {held && <p>Der Betreiber hält diese Position</p>}

      <h2>A. Überblick</h2>
      <p>
        Ticker: <span>{snapshot.ticker}</span>
      </p>
      <p>
        ISIN: <span>{snapshot.isin}</span>
      </p>
      <p>
        Land: <span>{snapshot.country?.trim() || '–'}</span>
      </p>
      <p>
        Beschreibung: <span>{snapshot.businessDescription?.trim() || '–'}</span>
      </p>
      <p>
        Gruppe:{' '}
        {snapshot.group === null ? (
          <span>–</span>
        ) : (
          <span>
            {snapshot.group.name} ({snapshot.group.source === 'AI' ? 'KI-Vorschlag' : 'manuell'})
          </span>
        )}
      </p>
      {user.role === 'ADMIN' && (
        <div>
          <label>
            Gruppe
            <select value={selectedGroup} onChange={(e) => setSelectedGroup(e.target.value)}>
              {groups.map((g) => (
                <option key={g.key} value={g.key}>
                  {g.name}
                </option>
              ))}
            </select>
          </label>
          <button type="button" onClick={saveGroup} disabled={saving || selectedGroup === ''}>
            Speichern
          </button>
          {saveError !== null && <p role="alert">{saveError}</p>}
        </div>
      )}

      <h2>B. Kennzahlen</h2>
      <CriteriaList keys={FUNDAMENTAL_CRITERIA} findings={snapshot.findings} />

      <h2>C. Qualitative Befunde</h2>
      <CriteriaList keys={QUALITATIVE_CRITERIA} findings={snapshot.findings} />

      <h2>D. Quartalsberichte</h2>
      <button type="button" disabled title="Noch nicht verfügbar">
        Upload
      </button>
      <p>{PLACEHOLDER}</p>

      <h2>E. KI-Chat</h2>
      <button type="button" disabled>
        Quartalsberichte zusammenfassen
      </button>
      <input disabled aria-label="Frage zur Aktie" />
      <p>{PLACEHOLDER}</p>

      <h2>F. Tiefenanalyse</h2>
      <DeepResearchPlaceholder />
    </section>
  )
}
