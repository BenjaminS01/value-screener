import { ApiError, apiFetch } from './apiClient'

export interface Finding {
  criterionKey: string
  numericValue: number | null
  booleanValue: boolean | null
  claim: string
  sourceUrl: string
  asOfDate: string
}

export type GroupSource = 'AI' | 'MANUAL'

export interface SnapshotGroup {
  key: string
  name: string
  source: GroupSource
}

export interface StockGroup {
  key: string
  name: string
  sortOrder: number
  stockCount: number
}

export interface CompanySnapshot {
  id: number
  ticker: string
  isin: string
  companyName: string
  country: string | null
  businessDescription: string
  group: SnapshotGroup | null
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

export function fetchGroups(): Promise<StockGroup[]> {
  return apiFetch<StockGroup[]>('/api/research/groups')
}

export function assignGroup(isin: string, groupKey: string): Promise<CompanySnapshot> {
  return apiFetch<CompanySnapshot>(`/api/research/snapshots/${encodeURIComponent(isin)}/group`, {
    method: 'PUT',
    body: { groupKey },
  })
}
