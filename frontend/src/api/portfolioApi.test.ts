import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buyPosition, correctPosition, deletePosition, fetchPortfolio, sellPosition } from './portfolioApi'

function okResponse(body: unknown = {}, status = 200) {
  return { ok: true, status, headers: new Headers(), json: async () => body }
}

describe('portfolioApi', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok123'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(okResponse()))
  })

  function lastCall() {
    const calls = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls
    const [path, options] = calls[calls.length - 1]
    return { path, options }
  }

  it('fetchPortfolio GETs /api/portfolio', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(
      okResponse([{ ticker: 'AAPL', isin: 'US1', companyName: 'Apple', group: null }]),
    )
    const result = await fetchPortfolio()
    expect(result).toHaveLength(1)
    const { path, options } = lastCall()
    expect(path).toBe('/api/portfolio')
    expect(options.method).toBe('GET')
  })

  it('buyPosition POSTs the body with the csrf header', async () => {
    const input = { ticker: 'AAPL', isin: 'US1', companyName: 'Apple', quantity: 2, entryPrice: 10.5, purchaseDate: '2026-01-15' }
    await buyPosition(input)
    const { path, options } = lastCall()
    expect(path).toBe('/api/portfolio')
    expect(options.method).toBe('POST')
    expect(options.headers['X-XSRF-TOKEN']).toBe('tok123')
    expect(JSON.parse(options.body)).toEqual(input)
  })

  it('sellPosition POSTs isin and quantity', async () => {
    await sellPosition('US1', 3)
    const { path, options } = lastCall()
    expect(path).toBe('/api/portfolio/sell')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ isin: 'US1', quantity: 3 })
  })

  it('correctPosition PUTs to the isin path', async () => {
    const input = { ticker: 'AAPL', companyName: 'Apple', quantity: 2, entryPrice: 10, purchaseDate: '2026-01-15' }
    await correctPosition('US1', input)
    const { path, options } = lastCall()
    expect(path).toBe('/api/portfolio/US1')
    expect(options.method).toBe('PUT')
    expect(options.headers['X-XSRF-TOKEN']).toBe('tok123')
    expect(JSON.parse(options.body)).toEqual(input)
  })

  it('deletePosition DELETEs the isin path and accepts 204', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true, status: 204, headers: new Headers() })
    await expect(deletePosition('US1')).resolves.toBeUndefined()
    const { path, options } = lastCall()
    expect(path).toBe('/api/portfolio/US1')
    expect(options.method).toBe('DELETE')
    expect(options.headers['X-XSRF-TOKEN']).toBe('tok123')
  })
})
