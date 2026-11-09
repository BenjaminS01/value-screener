import { apiFetch } from './apiClient'

export interface GroupRef {
  key: string
  name: string
}

export interface Position {
  ticker: string
  isin: string
  companyName: string
  group: GroupRef | null
}

export interface AdminPosition extends Position {
  quantity: number
  entryPrice: number
  purchaseDate: string
}

export function fetchPortfolio(): Promise<Position[] | AdminPosition[]> {
  return apiFetch<Position[] | AdminPosition[]>('/api/portfolio')
}

export interface BuyPositionInput {
  ticker: string
  isin: string
  companyName: string
  quantity: number
  entryPrice: number
  purchaseDate: string
}

export async function buyPosition(input: BuyPositionInput): Promise<void> {
  await apiFetch<void>('/api/portfolio', { method: 'POST', body: input })
}

export async function sellPosition(isin: string, quantity: number): Promise<void> {
  await apiFetch<void>('/api/portfolio/sell', { method: 'POST', body: { isin, quantity } })
}

export interface CorrectPositionInput {
  ticker: string
  companyName: string
  quantity: number
  entryPrice: number
  purchaseDate: string
}

export async function correctPosition(isin: string, input: CorrectPositionInput): Promise<void> {
  await apiFetch<void>(`/api/portfolio/${encodeURIComponent(isin)}`, { method: 'PUT', body: input })
}

export async function deletePosition(isin: string): Promise<void> {
  await apiFetch<void>(`/api/portfolio/${encodeURIComponent(isin)}`, { method: 'DELETE' })
}

// Kept only until Task 4 rewrites ResearchLibraryPage (still imports it).
export interface PublicPosition {
  ticker: string
  companyName: string
  isin: string
}

export function fetchPublicPositions(): Promise<PublicPosition[]> {
  return apiFetch<PublicPosition[]>('/api/portfolio/public')
}
