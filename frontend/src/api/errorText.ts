import { ApiError } from './apiClient'

export function writeErrorText(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'Not allowed'
    if (err.status === 409) return 'The position was changed in the meantime, please reload'
    return err.message
  }
  return err instanceof Error ? err.message : 'Unknown error'
}
