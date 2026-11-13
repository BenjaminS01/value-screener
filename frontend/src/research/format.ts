export function formatValue(f: { numericValue: number | null; booleanValue: boolean | null }): string | null {
  if (f.numericValue !== null) return f.numericValue.toLocaleString('de-DE', { maximumFractionDigits: 2 })
  if (f.booleanValue !== null) return f.booleanValue ? 'ja' : 'nein'
  return null
}

export function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' })
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' })
}
