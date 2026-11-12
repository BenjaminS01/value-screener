import { ApiError, apiFetch } from './apiClient'

export type ThesisStatus = 'WATCHING' | 'REJECTED'

export interface InvestmentThesis {
  isin: string
  whyInteresting: string | null
  expectedDevelopment: string | null
  exitCriteria: string | null
  status: ThesisStatus | null
  statusReason: string | null
  updatedAt: string
  version: number
  holding: boolean
}

export interface SaveThesisInput {
  whyInteresting: string
  expectedDevelopment: string
  exitCriteria: string
  status: ThesisStatus | null
  statusReason: string
  version: number | null
}

function path(isin: string): string {
  return `/api/research/theses/${encodeURIComponent(isin)}`
}

export async function fetchThesis(isin: string): Promise<InvestmentThesis | null> {
  try {
    return await apiFetch<InvestmentThesis>(path(isin))
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null
    throw e
  }
}

export function saveThesis(isin: string, input: SaveThesisInput): Promise<InvestmentThesis> {
  return apiFetch<InvestmentThesis>(path(isin), { method: 'PUT', body: input })
}
