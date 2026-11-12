// Parses user-typed numbers; accepts "," or "." as decimal separator, no thousands separators.
export function parseDecimal(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d+([.,]\d+)?$/.test(trimmed)) return null
  return Number(trimmed.replace(',', '.'))
}
