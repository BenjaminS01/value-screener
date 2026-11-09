import { ApiError, apiFetch } from './apiClient'

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

export function fetchAllSnapshots(): Promise<CompanySnapshot[]> {
  return apiFetch<CompanySnapshot[]>('/api/research/snapshots')
}

export async function fetchSnapshot(isin: string): Promise<CompanySnapshot | null> {
  try {
    return await apiFetch<CompanySnapshot>(`/api/research/snapshots/${encodeURIComponent(isin)}`)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null
    throw e
  }
}
