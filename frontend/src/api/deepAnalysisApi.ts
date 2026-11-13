import { apiFetch } from './apiClient'

export interface DeepAnalysisQuota {
  remainingToday: number
  limitPerDay: number
}

export interface DeepAnalysisResult {
  status: string
  message: string
  greeting: string
  functionName: string
  functionVersion: string
  region: string
  memoryLimitMb: number
  awsRequestId: string
  coldStart: boolean
  invokedAt: string
  source: 'LAMBDA' | 'LOCAL_STUB'
  remainingToday: number
  limitPerDay: number
}

export function fetchDeepAnalysisQuota(): Promise<DeepAnalysisQuota> {
  return apiFetch<DeepAnalysisQuota>('/api/research/deep-analysis/quota')
}

export function runDeepAnalysis(isin: string): Promise<DeepAnalysisResult> {
  return apiFetch<DeepAnalysisResult>(`/api/research/snapshots/${encodeURIComponent(isin)}/deep-analysis`, {
    method: 'POST',
  })
}
