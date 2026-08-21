export interface Finding {
  criterionKey: string
  numericValue: number | null
  booleanValue: boolean | null
  claim: string
  sourceUrl: string
  asOfDate: string
}

export interface CompanySnapshot {
  id: number
  ticker: string
  isin: string
  companyName: string
  sector: string
  country: string
  businessDescription: string
  findings: Finding[]
}

export async function fetchAllSnapshots(): Promise<CompanySnapshot[]> {
  const response = await fetch('/api/research/snapshots')
  if (!response.ok) {
    throw new Error(`Failed to load research snapshots: ${response.status}`)
  }
  return response.json()
}

export async function fetchSnapshot(isin: string): Promise<CompanySnapshot | null> {
  const response = await fetch(`/api/research/snapshots/${isin}`)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`Failed to load research snapshot: ${response.status}`)
  }
  return response.json()
}
