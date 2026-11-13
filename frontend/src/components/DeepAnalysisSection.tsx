import { useEffect, useState } from 'react'
import { writeErrorText } from '../api/errorText'
import {
  DeepAnalysisQuota,
  DeepAnalysisResult,
  fetchDeepAnalysisQuota,
  runDeepAnalysis,
} from '../api/deepAnalysisApi'
import { formatDateTime } from '../research/format'

interface Props {
  isin: string
}

export function DeepAnalysisSection({ isin }: Props) {
  const [quota, setQuota] = useState<DeepAnalysisQuota | null>(null)
  const [result, setResult] = useState<DeepAnalysisResult | null>(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // The quota line is optional: a failing load only hides it.
    fetchDeepAnalysisQuota().then(
      (q) => {
        if (!cancelled) setQuota(q)
      },
      () => {},
    )
    return () => {
      cancelled = true
    }
  }, [])

  async function run() {
    setRunning(true)
    setError(null)
    try {
      const r = await runDeepAnalysis(isin)
      setResult(r)
      setQuota({ remainingToday: r.remainingToday, limitPerDay: r.limitPerDay })
    } catch (e) {
      setError(writeErrorText(e))
    } finally {
      setRunning(false)
    }
  }

  const exhausted = quota !== null && quota.remainingToday <= 0

  return (
    <div>
      {quota !== null && <p>Heute noch {quota.remainingToday} von {quota.limitPerDay} Analysen</p>}
      <button type="button" onClick={run} disabled={running || exhausted}>
        Tiefenanalyse starten
      </button>
      {error !== null && <p role="alert">{error}</p>}
      {result !== null && (
        <div>
          <p>{result.message}</p>
          <p>{result.greeting}</p>
          <p>Funktion: {result.functionName}</p>
          <p>Version: {result.functionVersion}</p>
          <p>Region: {result.region}</p>
          <p>Speicher (MB): {result.memoryLimitMb}</p>
          <p>Request-ID: {result.awsRequestId}</p>
          <p>Kaltstart: {result.coldStart ? 'ja' : 'nein'}</p>
          <p>Ausgeführt am: {formatDateTime(result.invokedAt)}</p>
          <p>Quelle: {result.source === 'LAMBDA' ? 'AWS Lambda' : 'lokale Simulation'}</p>
        </div>
      )}
    </div>
  )
}
