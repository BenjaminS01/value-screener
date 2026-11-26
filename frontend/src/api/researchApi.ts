import { ApiError, apiFetch } from './apiClient'

export interface Finding {
  criterionKey: string
  numericValue: number | null
  booleanValue: boolean | null
  claim: string
  sourceUrl: string
  asOfDate: string
  stale: boolean
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
  ticker: string
  isin: string
  companyName: string
  country: string | null
  businessDescription: string
  group: SnapshotGroup | null
  findings: Finding[]
  lastResearchedAt: string | null
  stale: boolean
}

/** One row of the research list: no findings or description (those come with fetchSnapshot). */
export interface SnapshotSummary {
  ticker: string
  isin: string
  companyName: string
  country: string | null
  group: SnapshotGroup | null
  lastResearchedAt: string | null
  stale: boolean
}

export function fetchAllSnapshots(): Promise<SnapshotSummary[]> {
  return apiFetch<SnapshotSummary[]>('/api/research/snapshots')
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

export type HistoryOrigin = 'INGEST' | 'SYNC' | 'MANUAL'

export interface FindingHistoryEntry {
  criterionKey: string
  numericValue: number | null
  booleanValue: boolean | null
  claim: string
  sourceUrl: string
  asOfDate: string
  replacedAt: string
  origin: HistoryOrigin
}

export interface SnapshotHistory {
  findings: FindingHistoryEntry[]
}

export function fetchHistory(isin: string): Promise<SnapshotHistory> {
  return apiFetch<SnapshotHistory>(`/api/research/snapshots/${encodeURIComponent(isin)}/history`)
}
