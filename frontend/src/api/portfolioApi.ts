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

export interface AddPositionInput {
  ticker: string
  isin: string
  companyName: string
  quantity: number
  entryPrice: number
  purchaseDate: string
}

export interface Credentials {
  username: string
  password: string
}

export async function buyPosition(credentials: Credentials, input: AddPositionInput): Promise<void> {
  const authHeader = 'Basic ' + btoa(`${credentials.username}:${credentials.password}`)
  const response = await fetch('/api/portfolio', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: authHeader,
    },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw new Error(`Failed to add position: ${response.status}`)
  }
}
