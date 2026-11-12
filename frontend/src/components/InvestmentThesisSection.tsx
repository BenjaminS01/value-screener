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

const CONFLICT = 'Die These wurde inzwischen geändert, bitte neu laden'
const STATUS_LABELS: Record<ThesisStatus, string> = { WATCHING: 'Beobachten', REJECTED: 'Abgelehnt' }
// React renders these strings as text nodes (never HTML); pre-wrap keeps the line breaks.
const PRE_WRAP = { whiteSpace: 'pre-wrap' } as const

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

function statusLine(t: InvestmentThesis): string {
  if (t.holding) return 'Halte ich'
  if (t.status === null) return 'Kein Status'
  return t.statusReason ? `${STATUS_LABELS[t.status]}: ${t.statusReason}` : STATUS_LABELS[t.status]
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
          setThesis(fresh)
          setForm(toForm(fresh))
        } catch {
          // keep the form as is; the conflict message already tells the user to reload
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
    <div>
      <h2>Investment-These</h2>
      <p>Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.</p>
      {loading && <p>Lade …</p>}
      {loadError !== null && <p role="alert">{loadError}</p>}
      {!loading && loadError === null && !editing && (
        <>
          {thesis === null ? (
            <p>Noch keine Investment-These</p>
          ) : (
            <>
              <h3>Warum interessant</h3>
              <p style={PRE_WRAP}>{thesis.whyInteresting}</p>
              <h3>Erwartete Entwicklung</h3>
              <p style={PRE_WRAP}>{thesis.expectedDevelopment}</p>
              <h3>Wann verkaufen oder verwerfen</h3>
              <p style={PRE_WRAP}>{thesis.exitCriteria}</p>
              <p>{statusLine(thesis)}</p>
              <p>Aktualisiert am {formatDate(thesis.updatedAt)}</p>
            </>
          )}
          {user.role === 'ADMIN' && (
            <button type="button" onClick={startEdit}>
              These bearbeiten
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
          aria-label="Investment-These bearbeiten"
        >
          <label>
            Warum interessant
            <textarea value={form.whyInteresting} onChange={(e) => setForm({ ...form, whyInteresting: e.target.value })} />
          </label>
          <label>
            Erwartete Entwicklung
            <textarea
              value={form.expectedDevelopment}
              onChange={(e) => setForm({ ...form, expectedDevelopment: e.target.value })}
            />
          </label>
          <label>
            Wann verkaufen oder verwerfen
            <textarea value={form.exitCriteria} onChange={(e) => setForm({ ...form, exitCriteria: e.target.value })} />
          </label>
          <label>
            Status
            <select value={form.status ?? ''} onChange={(e) => setStatus(e.target.value)} disabled={holding}>
              <option value="">Kein Status</option>
              <option value="WATCHING">Beobachten</option>
              <option value="REJECTED">Abgelehnt</option>
            </select>
          </label>
          {holding && <p>Status gilt nicht, solange die Position gehalten wird</p>}
          <label>
            Begründung
            <input
              value={form.statusReason}
              onChange={(e) => setForm({ ...form, statusReason: e.target.value })}
              disabled={holding || form.status === null}
            />
          </label>
          <button type="submit" disabled={saving}>
            Speichern
          </button>
          <button type="button" onClick={() => setEditing(false)} disabled={saving}>
            Abbrechen
          </button>
          {saveError !== null && <p role="alert">{saveError}</p>}
        </form>
      )}
    </div>
  )
}
