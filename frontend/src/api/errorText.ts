import { ApiError } from './apiClient'

export function writeErrorText(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'Keine Berechtigung'
    if (err.status === 409) return 'Die Position wurde inzwischen geändert, bitte neu laden'
    return err.message
  }
  return err instanceof Error ? err.message : 'Unbekannter Fehler'
}
