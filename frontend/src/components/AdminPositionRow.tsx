import { FormEvent, useState } from 'react'
import { ApiError } from '../api/apiClient'
import { writeErrorText } from '../api/errorText'
import { parseDecimal } from '../api/parseDecimal'
import { AdminPosition, correctPosition, deletePosition, sellPosition } from '../api/portfolioApi'
import { formatDate } from '../research/format'

interface AdminPositionRowProps {
  position: AdminPosition
  onOpen: (isin: string) => void
  // conflictMessage is set when the backend answered 409; the page reloads and shows it.
  onChanged: (conflictMessage?: string) => void
}

const INVALID_NUMBER = 'Please enter a valid number'

function formatNumber(value: number, fractionDigits?: number): string {
  return value.toLocaleString('en-US', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits ?? 20,
  })
}


type Mode = 'idle' | 'sell' | 'correct'

/**
 * One portfolio row for ADMIN. "Correct" turns the row's own cells into inputs (no second form below
 * the table); "Save" without a change only leaves edit mode.
 */
export function AdminPositionRow({ position, onOpen, onChanged }: AdminPositionRowProps) {
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

  function close() {
    setError(null)
    setMode('idle')
  }

  function openSell() {
    setSellQuantity('')
    setError(null)
    setMode('sell')
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
    if (parsed === null || parsed <= 0) {
      setError(INVALID_NUMBER)
      return
    }
    run(() => sellPosition(position.isin, parsed))
  }

  function handleCorrect(event: FormEvent) {
    event.preventDefault()
    const parsedQuantity = parseDecimal(quantity)
    const parsedPrice = parseDecimal(entryPrice)
    if (parsedQuantity === null || parsedPrice === null || parsedQuantity <= 0 || parsedPrice <= 0) {
      setError(INVALID_NUMBER)
      return
    }
    const unchanged =
      ticker.trim() === position.ticker &&
      companyName.trim() === position.companyName &&
      parsedQuantity === position.quantity &&
      parsedPrice === position.entryPrice &&
      purchaseDate === position.purchaseDate
    if (unchanged) {
      close()
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
    if (!window.confirm(`Really delete position ${position.companyName}?`)) return
    run(() => deletePosition(position.isin))
  }

  const formId = `correct-${position.isin}`
  const editing = mode === 'correct'

  return (
    <tr>
      <td>
        {editing ? (
          <input aria-label="Company" form={formId} value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
        ) : (
          <button type="button" onClick={() => onOpen(position.isin)}>
            {position.companyName}
          </button>
        )}
      </td>
      <td>
        {editing ? (
          <input aria-label="Ticker" form={formId} value={ticker} onChange={(e) => setTicker(e.target.value)} />
        ) : (
          position.ticker
        )}
      </td>
      <td>{position.group ? position.group.name : '—'}</td>
      <td>
        {editing ? (
          <input aria-label="Quantity" form={formId} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
        ) : (
          formatNumber(position.quantity)
        )}
      </td>
      <td>
        {editing ? (
          <input aria-label="Entry price" form={formId} value={entryPrice} onChange={(e) => setEntryPrice(e.target.value)} />
        ) : (
          formatNumber(position.entryPrice, 2)
        )}
      </td>
      <td>
        {editing ? (
          <input
            aria-label="Purchase date"
            type="date"
            form={formId}
            value={purchaseDate}
            onChange={(e) => setPurchaseDate(e.target.value)}
          />
        ) : (
          formatDate(position.purchaseDate)
        )}
      </td>
      <td>
        {mode === 'idle' && (
          <>
            <button type="button" disabled={pending} onClick={openSell}>
              Partial sale
            </button>
            <button type="button" disabled={pending} onClick={openCorrect}>
              Correct
            </button>
            <button type="button" disabled={pending} onClick={handleDelete}>
              Delete
            </button>
          </>
        )}
        {mode === 'sell' && (
          <form onSubmit={handleSell}>
            <label>
              Quantity to sell
              <input value={sellQuantity} onChange={(e) => setSellQuantity(e.target.value)} />
            </label>
            <button type="submit" disabled={pending}>
              Confirm partial sale
            </button>
            <button type="button" onClick={close}>
              Cancel
            </button>
          </form>
        )}
        {editing && (
          <form id={formId} onSubmit={handleCorrect}>
            <button type="submit" disabled={pending}>
              Save
            </button>
            <button type="button" onClick={close} disabled={pending}>
              Cancel
            </button>
          </form>
        )}
        {error && <p role="alert">{error}</p>}
      </td>
    </tr>
  )
}
