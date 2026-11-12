import { FormEvent, useState } from 'react'
import { writeErrorText } from '../api/errorText'
import { parseDecimal } from '../api/parseDecimal'
import { buyPosition } from '../api/portfolioApi'

const INVALID_NUMBER = 'Bitte eine gültige Zahl eingeben'

interface AddPositionFormProps {
  onAdded: () => void
}

export function AddPositionForm({ onAdded }: AddPositionFormProps) {
  const [ticker, setTicker] = useState('')
  const [isin, setIsin] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [quantity, setQuantity] = useState('')
  const [entryPrice, setEntryPrice] = useState('')
  const [purchaseDate, setPurchaseDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const parsedQuantity = parseDecimal(quantity)
    const parsedPrice = parseDecimal(entryPrice)
    if (parsedQuantity === null || parsedPrice === null || parsedQuantity <= 0 || parsedPrice <= 0) {
      setError(INVALID_NUMBER)
      return
    }
    setPending(true)
    try {
      await buyPosition({
        ticker,
        isin,
        companyName,
        quantity: parsedQuantity,
        entryPrice: parsedPrice,
        purchaseDate,
      })
      setTicker('')
      setIsin('')
      setCompanyName('')
      setQuantity('')
      setEntryPrice('')
      setPurchaseDate('')
      onAdded()
    } catch (err) {
      setError(writeErrorText(err))
    } finally {
      setPending(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Position kaufen">
      <label>
        Ticker
        <input value={ticker} onChange={(e) => setTicker(e.target.value)} />
      </label>
      <label>
        ISIN
        <input value={isin} onChange={(e) => setIsin(e.target.value)} />
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
        Kaufen
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}
