export function formatValue(f: { numericValue: number | null; booleanValue: boolean | null }): string | null {
  if (f.numericValue !== null) return f.numericValue.toLocaleString('en-US', { maximumFractionDigits: 2 })
  if (f.booleanValue !== null) return f.booleanValue ? 'yes' : 'no'
  return null
}

export function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' })
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' })
}
