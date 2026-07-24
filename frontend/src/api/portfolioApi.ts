export interface PublicPosition {
  ticker: string
  companyName: string
}

export async function fetchPublicPositions(): Promise<PublicPosition[]> {
  const response = await fetch('/api/portfolio/public')
  if (!response.ok) {
    throw new Error(`Failed to load portfolio: ${response.status}`)
  }
  return response.json()
}
