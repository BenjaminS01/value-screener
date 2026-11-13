import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchDeepAnalysisQuota, runDeepAnalysis } from './deepAnalysisApi'

describe('deepAnalysisApi', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok123'
    vi.stubGlobal('fetch', vi.fn())
  })
  const fetchMock = () => fetch as unknown as ReturnType<typeof vi.fn>

  it('fetchDeepAnalysisQuota GETs the quota path', async () => {
    const quota = { remainingToday: 5, limitPerDay: 5 }
    fetchMock().mockResolvedValue(new Response(JSON.stringify(quota), { status: 200 }))
    await expect(fetchDeepAnalysisQuota()).resolves.toEqual(quota)
    expect(fetchMock().mock.calls[0][0]).toBe('/api/research/deep-analysis/quota')
  })

  it('runDeepAnalysis POSTs with the csrf header', async () => {
    const result = { status: 'NOT_IMPLEMENTED', source: 'LOCAL_STUB', remainingToday: 4, limitPerDay: 5 }
    fetchMock().mockResolvedValue(new Response(JSON.stringify(result), { status: 200 }))
    await expect(runDeepAnalysis('US0378331005')).resolves.toEqual(result)
    const [path, options] = fetchMock().mock.calls[0]
    expect(path).toBe('/api/research/snapshots/US0378331005/deep-analysis')
    expect(options.method).toBe('POST')
    expect(options.headers['X-XSRF-TOKEN']).toBe('tok123')
  })

  it('runDeepAnalysis surfaces the backend error message', async () => {
    fetchMock().mockResolvedValue(
      new Response(JSON.stringify({ error: 'Tageslimit für die Tiefenanalyse erreicht' }), { status: 429 }),
    )
    await expect(runDeepAnalysis('US0378331005')).rejects.toThrow('Tageslimit für die Tiefenanalyse erreicht')
  })
})
