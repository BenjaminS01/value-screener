import { useState } from 'react'
import type { FindingHistoryEntry } from '../api/researchApi'
import { isSafeHttpUrl } from '../research/criteria'
import { formatDate, formatValue } from '../research/format'

export function FindingHistory({ entries }: { entries: FindingHistoryEntry[] }) {
  const [open, setOpen] = useState(false)
  if (entries.length === 0) return null
  return (
    <div>
      <button type="button" aria-expanded={open} onClick={() => setOpen(!open)}>
        Verlauf ({entries.length})
      </button>
      {open && (
        <ul>
          {entries.map((e, i) => (
            <li key={i}>
              {formatValue(e) !== null && <strong>{formatValue(e)}</strong>} <span>{e.claim}</span>{' '}
              <span>{formatDate(e.asOfDate)}</span>{' '}
              {isSafeHttpUrl(e.sourceUrl) ? (
                <a href={e.sourceUrl} target="_blank" rel="noopener noreferrer">
                  Quelle
                </a>
              ) : (
                <span>{e.sourceUrl}</span>
              )}{' '}
              <span>ersetzt am {formatDate(e.replacedAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
