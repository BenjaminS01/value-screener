import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/apiClient'
import type { AuthUser } from '../api/authApi'
import { writeErrorText } from '../api/errorText'
import { fetchThesis, InvestmentThesis, saveThesis, ThesisStatus } from '../api/thesisApi'
import { formatDate } from '../research/format'

interface Props {
  user: AuthUser
  isin: string
}

const CONFLICT = 'The thesis was changed in the meantime, please reload'
const STATUS_LABELS: Record<ThesisStatus, string> = { WATCHING: 'Watching', REJECTED: 'Rejected' }
// React renders these strings as text nodes (never HTML); pre-wrap keeps the line breaks.

interface FormState {
  whyInteresting: string
  expectedDevelopment: string
  exitCriteria: string
  status: ThesisStatus | null
  statusReason: string
}

function toForm(t: InvestmentThesis | null): FormState {
  return {
    whyInteresting: t?.whyInteresting ?? '',
    expectedDevelopment: t?.expectedDevelopment ?? '',
    exitCriteria: t?.exitCriteria ?? '',
    status: t?.status ?? null,
    statusReason: t?.statusReason ?? '',
  }
}

const STATUS_BADGE: Record<ThesisStatus, string> = { WATCHING: 'badge--watching', REJECTED: 'badge--rejected' }

function StatusLine({ thesis: t }: { thesis: InvestmentThesis }) {
  if (t.holding)
    return (
      <p>
        <span className="badge badge--holding">Holding</span>
      </p>
    )
  if (t.status === null)
    return (
      <p>
        <span className="badge badge--muted">No status</span>
      </p>
    )
  return (
    <p>
      <span className={`badge ${STATUS_BADGE[t.status]}`}>{STATUS_LABELS[t.status]}</span>
      {t.statusReason && <> {t.statusReason}</>}
    </p>
  )
}

export function InvestmentThesisSection({ user, isin }: Props) {
  const [thesis, setThesis] = useState<InvestmentThesis | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<FormState>(toForm(null))
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fetchThesis(isin)
      .then((t) => {
        if (!cancelled) setThesis(t)
      })
      .catch((e) => {
        if (!cancelled) setLoadError(writeErrorText(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [isin])

  const startEdit = useCallback(() => {
    setForm(toForm(thesis))
    setSaveError(null)
    setEditing(true)
  }, [thesis])

  async function save() {
    setSaving(true)
    setSaveError(null)
    try {
      const saved = await saveThesis(isin, {
        ...form,
        // No status means no reason; the backend rejects a reason without a status.
        statusReason: form.status === null ? '' : form.statusReason,
        version: thesis?.version ?? null,
      })
      setThesis(saved)
      setEditing(false)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setSaveError(CONFLICT)
        try {
          const fresh = await fetchThesis(isin)
          // Only the stored thesis is refreshed (so the next save carries the new version);
          // the typed values stay in the open form.
          setThesis(fresh)
        } catch {
          // keep everything as is; the conflict message already tells the user to reload
        }
      } else {
        setSaveError(writeErrorText(e))
      }
    } finally {
      setSaving(false)
    }
  }

  function setStatus(value: string) {
    const status = value === '' ? null : (value as ThesisStatus)
    setForm((f) => ({ ...f, status, statusReason: status === null ? '' : f.statusReason }))
  }

  const holding = thesis?.holding ?? false

  return (
    <div className="card">
      <h2>Investment thesis</h2>
      <p className="muted">Personal view of the operator, not investment advice.</p>
      {loading && <p className="muted">Loading …</p>}
      {loadError !== null && <p role="alert">{loadError}</p>}
      {!loading && loadError === null && !editing && (
        <>
          {thesis === null ? (
            <p className="muted">No investment thesis yet</p>
          ) : (
            <>
              <h3>Why interesting</h3>
              <p className="pre-wrap">{thesis.whyInteresting}</p>
              <h3>Expected development</h3>
              <p className="pre-wrap">{thesis.expectedDevelopment}</p>
              <h3>When to sell or drop</h3>
              <p className="pre-wrap">{thesis.exitCriteria}</p>
              <StatusLine thesis={thesis} />
              <p className="muted">Updated on {formatDate(thesis.updatedAt)}</p>
            </>
          )}
          {user.role === 'ADMIN' && (
            <button type="button" onClick={startEdit}>
              Edit thesis
            </button>
          )}
        </>
      )}
      {editing && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void save()
          }}
          aria-label="Edit investment thesis"
        >
          <label>
            Why interesting
            <textarea
              value={form.whyInteresting}
              onChange={(e) => setForm({ ...form, whyInteresting: e.target.value })}
            />
          </label>
          <label>
            Expected development
            <textarea
              value={form.expectedDevelopment}
              onChange={(e) => setForm({ ...form, expectedDevelopment: e.target.value })}
            />
          </label>
          <label>
            When to sell or drop
            <textarea value={form.exitCriteria} onChange={(e) => setForm({ ...form, exitCriteria: e.target.value })} />
          </label>
          <label>
            Status
            <select value={form.status ?? ''} onChange={(e) => setStatus(e.target.value)} disabled={holding}>
              <option value="">No status</option>
              <option value="WATCHING">Watching</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </label>
          {holding && <p>Status does not apply while the position is held</p>}
          <label>
            Reason
            <input
              value={form.statusReason}
              onChange={(e) => setForm({ ...form, statusReason: e.target.value })}
              disabled={holding || form.status === null}
            />
          </label>
          <button type="submit" disabled={saving}>
            Save
          </button>
          <button type="button" onClick={() => setEditing(false)} disabled={saving}>
            Cancel
          </button>
          {saveError !== null && <p role="alert">{saveError}</p>}
        </form>
      )}
    </div>
  )
}
