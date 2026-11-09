import { FormEvent, useState } from 'react'
import { ApiError } from '../api/apiClient'
import { writeErrorText } from '../api/errorText'
import { parseDecimal } from '../api/parseDecimal'
import { AdminPosition, correctPosition, deletePosition, sellPosition } from '../api/portfolioApi'

interface PositionActionsProps {
  position: AdminPosition
  // conflictMessage is set when the backend answered 409; the page reloads and shows it.
  onChanged: (conflictMessage?: string) => void
}

const INVALID_NUMBER = 'Bitte eine gültige Zahl eingeben'

type Mode = 'idle' | 'sell' | 'correct'

export function PositionActions({ position, onChanged }: PositionActionsProps) {
  const [mode, setMode] = useState<Mode>('idle')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [sellQuantity, setSellQuantity] = useState('')
  const [ticker, setTicker] = useState(position.ticker)
  const [companyName, setCompanyName] = useState(position.companyName)
  const [quantity, setQuantity] = useState(String(position.quantity))
  const [entryPrice, setEntryPrice] = useState(String(position.entryPrice))
  const [purchaseDate, setPurchaseDate] = useState(position.purchaseDate)

  async function run(action: () => Promise<void>) {
    setError(null)
    setPending(true)
    try {
      await action()
      setMode('idle')
      onChanged()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setMode('idle')
        onChanged(writeErrorText(err))
      } else {
        setError(writeErrorText(err))
      }
    } finally {
      setPending(false)
    }
  }

  function openCorrect() {
    setTicker(position.ticker)
    setCompanyName(position.companyName)
    setQuantity(String(position.quantity))
    setEntryPrice(String(position.entryPrice))
    setPurchaseDate(position.purchaseDate)
    setError(null)
    setMode('correct')
  }

  function handleSell(event: FormEvent) {
    event.preventDefault()
    const parsed = parseDecimal(sellQuantity)
    if (parsed === null) {
      setError(INVALID_NUMBER)
      return
    }
    run(() => sellPosition(position.isin, parsed))
  }

  function handleCorrect(event: FormEvent) {
    event.preventDefault()
    const parsedQuantity = parseDecimal(quantity)
    const parsedPrice = parseDecimal(entryPrice)
    if (parsedQuantity === null || parsedPrice === null) {
      setError(INVALID_NUMBER)
      return
    }
    run(() =>
      correctPosition(position.isin, {
        ticker,
        companyName,
        quantity: parsedQuantity,
        entryPrice: parsedPrice,
        purchaseDate,
      }),
    )
  }

  function handleDelete() {
    if (!window.confirm(`Position ${position.companyName} wirklich löschen?`)) return
    run(() => deletePosition(position.isin))
  }

  return (
    <div>
      <button type="button" disabled={pending} onClick={() => { setError(null); setMode('sell') }}>
        Verkaufen
      </button>
      <button type="button" disabled={pending} onClick={openCorrect}>
        Korrigieren
      </button>
      <button type="button" disabled={pending} onClick={handleDelete}>
        Löschen
      </button>
      {mode === 'sell' && (
        <form onSubmit={handleSell}>
          <label>
            Verkaufsmenge
            <input value={sellQuantity} onChange={(e) => setSellQuantity(e.target.value)} />
          </label>
          <button type="submit" disabled={pending}>
            Verkauf bestätigen
          </button>
          <button type="button" onClick={() => setMode('idle')}>
            Abbrechen
          </button>
        </form>
      )}
      {mode === 'correct' && (
        <form onSubmit={handleCorrect}>
          <label>
            Ticker
            <input value={ticker} onChange={(e) => setTicker(e.target.value)} />
          </label>
          <label>
            Unternehmen
            <input value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
          </label>
          <label>
            Stückzahl
            <input value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </label>
          <label>
            Einstandskurs
            <input value={entryPrice} onChange={(e) => setEntryPrice(e.target.value)} />
          </label>
          <label>
            Kaufdatum
            <input type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} />
          </label>
          <button type="submit" disabled={pending}>
            Speichern
          </button>
          <button type="button" onClick={() => setMode('idle')}>
            Abbrechen
          </button>
        </form>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  )
}
