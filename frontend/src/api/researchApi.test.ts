import { beforeEach, describe, expect, it, vi } from 'vitest'
import { assignGroup, fetchAllSnapshots, fetchGroups, fetchSnapshot } from './researchApi'

describe('researchApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('fetchAllSnapshots returns snapshots from the backend', async () => {
    const snapshot = {
      id: 1,
      ticker: 'AAPL',
      isin: 'US0378331005',
      companyName: 'Apple Inc.',
      country: 'US',
      group: { key: 'tech', name: 'Technologie', source: 'AI' },
      businessDescription: 'Consumer electronics.',
      findings: [
        {
          criterionKey: 'PE_RATIO',
          numericValue: 28.5,
          booleanValue: null,
          claim: 'P/E of 28.5 as of the latest filing.',
          sourceUrl: 'https://example.com/aapl',
          asOfDate: '2026-08-01',
        },
      ],
    }
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => [snapshot],
    })

    const result = await fetchAllSnapshots()

    expect(result).toEqual([snapshot])
    expect(fetch).toHaveBeenCalledWith('/api/research/snapshots', expect.objectContaining({ method: 'GET' }))
  })

  it('fetchAllSnapshots throws when the backend request fails', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 500,
      headers: new Headers(),
      json: async () => [],
    })

    await expect(fetchAllSnapshots()).rejects.toThrow('HTTP 500')
  })

  it('fetchSnapshot returns the snapshot for a known isin', async () => {
    const snapshot = {
      id: 1,
      ticker: 'AAPL',
      isin: 'US0378331005',
      companyName: 'Apple Inc.',
      country: 'US',
      group: { key: 'tech', name: 'Technologie', source: 'AI' },
      businessDescription: 'Consumer electronics.',
      findings: [],
    }
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => snapshot,
    })

    const result = await fetchSnapshot('US0378331005')

    expect(result).toEqual(snapshot)
    expect(fetch).toHaveBeenCalledWith('/api/research/snapshots/US0378331005', expect.objectContaining({ method: 'GET' }))
  })

  it('fetchSnapshot returns null for an unknown isin', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 404,
      headers: new Headers(),
      json: async () => ({}),
    })

    const result = await fetchSnapshot('US9999999999')

    expect(result).toBeNull()
  })

  it('fetchGroups returns the group catalog', async () => {
    const groups = [{ key: 'tech', name: 'Technologie', sortOrder: 1, stockCount: 2 }]
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true, status: 200, headers: new Headers(), json: async () => groups,
    })
    expect(await fetchGroups()).toEqual(groups)
    expect(fetch).toHaveBeenCalledWith('/api/research/groups', expect.objectContaining({ method: 'GET' }))
  })

  it('assignGroup PUTs the group key and returns the updated snapshot', async () => {
    document.cookie = 'XSRF-TOKEN=tok'
    const updated = { id: 1, isin: 'US0378331005' }
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true, status: 200, headers: new Headers(), json: async () => updated,
    })
    expect(await assignGroup('US0378331005', 'tech')).toEqual(updated)
    expect(fetch).toHaveBeenCalledWith(
      '/api/research/snapshots/US0378331005/group',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ groupKey: 'tech' }) }),
    )
  })
})
