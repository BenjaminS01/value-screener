import { FormEvent, useState } from 'react'
import { buyPosition, Credentials } from '../api/portfolioApi'

interface AddPositionFormProps {
  credentials: Credentials
  onAdded: () => void
}

export function AddPositionForm({ credentials, onAdded }: AddPositionFormProps) {
  const [ticker, setTicker] = useState('')
  const [isin, setIsin] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [quantity, setQuantity] = useState('')
  const [entryPrice, setEntryPrice] = useState('')
  const [purchaseDate, setPurchaseDate] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await buyPosition(credentials, {
        ticker,
        isin,
        companyName,
        quantity: Number(quantity),
        entryPrice: Number(entryPrice),
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
      setError((err as Error).message)
    }
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Position hinzufügen">
      <label>
        Ticker
        <input value={ticker} onChange={(e) => setTicker(e.target.value)} />
      </label>
      <label>
        ISIN
        <input value={isin} onChange={(e) => setIsin(e.target.value)} />
      </label>
      <label>
        Unternehmensname
        <input value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
      </label>
      <label>
        Stückzahl
        <input value={quantity} onChange={(e) => setQuantity(e.target.value)} />
      </label>
      <label>
        Einstiegspreis
        <input value={entryPrice} onChange={(e) => setEntryPrice(e.target.value)} />
      </label>
      <label>
        Kaufdatum
        <input type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} />
      </label>
      <button type="submit">Hinzufügen</button>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}
