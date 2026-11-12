import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchThesis, saveThesis } from './thesisApi'

const thesis = {
  isin: 'US0378331005', whyInteresting: 'a', expectedDevelopment: null, exitCriteria: null,
  status: null, statusReason: null, updatedAt: '2026-11-01T10:00:00Z', version: 2, holding: false,
}

describe('thesisApi', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok123'
    vi.stubGlobal('fetch', vi.fn())
  })
  const fetchMock = () => fetch as unknown as ReturnType<typeof vi.fn>

  it('fetchThesis GETs the thesis path', async () => {
    fetchMock().mockResolvedValue(new Response(JSON.stringify(thesis), { status: 200 }))
    await expect(fetchThesis('US0378331005')).resolves.toEqual(thesis)
    expect(fetchMock().mock.calls[0][0]).toBe('/api/research/theses/US0378331005')
  })

  it('fetchThesis returns null on 404', async () => {
    fetchMock().mockResolvedValue(new Response(JSON.stringify({ error: 'Thesis not found' }), { status: 404 }))
    await expect(fetchThesis('US0378331005')).resolves.toBeNull()
  })

  it('fetchThesis rethrows other errors', async () => {
    fetchMock().mockResolvedValue(new Response(JSON.stringify({ error: 'boom' }), { status: 500 }))
    await expect(fetchThesis('US0378331005')).rejects.toThrow('boom')
  })

  it('saveThesis PUTs the body with the csrf header', async () => {
    fetchMock().mockResolvedValue(new Response(JSON.stringify(thesis), { status: 200 }))
    const input = { whyInteresting: 'a', expectedDevelopment: '', exitCriteria: '', status: null, statusReason: '', version: null }
    await expect(saveThesis('US0378331005', input)).resolves.toEqual(thesis)
    const [path, options] = fetchMock().mock.calls[0]
    expect(path).toBe('/api/research/theses/US0378331005')
    expect(options.method).toBe('PUT')
    expect(options.headers['X-XSRF-TOKEN']).toBe('tok123')
    expect(JSON.parse(options.body)).toEqual(input)
  })
})
