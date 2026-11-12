import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InvestmentThesisSection } from './InvestmentThesisSection'
import type { AuthUser } from '../api/authApi'

const admin: AuthUser = { username: 'ben', role: 'ADMIN' }
const guest: AuthUser = { username: 'guest', role: 'USER' }
const ISIN = 'US0378331005'

function thesis(over: Record<string, unknown> = {}) {
  return {
    isin: ISIN, whyInteresting: 'Starker Burggraben', expectedDevelopment: 'Wachstum', exitCriteria: 'Moat bricht weg',
    status: 'WATCHING', statusReason: 'Zu teuer', updatedAt: '2026-11-01T10:00:00Z', version: 3, holding: false,
    ...over,
  }
}

let getResponse: () => Response
let putResponse: () => Response
let putBodies: unknown[]

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status })
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=tok'
  putBodies = []
  getResponse = () => json(200, thesis())
  putResponse = () => json(200, thesis())
  vi.stubGlobal('fetch', vi.fn((_url: string, init?: RequestInit) => {
    if (init?.method === 'PUT') {
      putBodies.push(JSON.parse(init.body as string))
      return Promise.resolve(putResponse())
    }
    return Promise.resolve(getResponse())
  }))
})

describe('InvestmentThesisSection', () => {
  it.each([admin, guest])('shows note, texts, status and date for %o', async (user) => {
    render(<InvestmentThesisSection user={user} isin={ISIN} />)
    expect(await screen.findByText('Starker Burggraben')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Investment-These' })).toBeInTheDocument()
    expect(screen.getByText('Persönliche Einschätzung des Betreibers, keine Anlageempfehlung.')).toBeInTheDocument()
    expect(screen.getByText('Warum interessant')).toBeInTheDocument()
    expect(screen.getByText('Erwartete Entwicklung')).toBeInTheDocument()
    expect(screen.getByText('Wann verkaufen oder verwerfen')).toBeInTheDocument()
    expect(screen.getByText('Wachstum')).toBeInTheDocument()
    expect(screen.getByText('Moat bricht weg')).toBeInTheDocument()
    expect(screen.getByText(/Beobachten/)).toHaveTextContent('Zu teuer')
    expect(screen.getByText('Aktualisiert am 01.11.2026')).toBeInTheDocument()
  })

  it('shows Halte ich instead of the status when holding', async () => {
    getResponse = () => json(200, thesis({ holding: true }))
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    expect(await screen.findByText('Halte ich')).toBeInTheDocument()
    expect(screen.queryByText(/Beobachten/)).not.toBeInTheDocument()
  })

  it('shows Kein Status when the status is null', async () => {
    getResponse = () => json(200, thesis({ status: null, statusReason: null }))
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    expect(await screen.findByText('Kein Status')).toBeInTheDocument()
  })

  it('shows a hint when there is no thesis', async () => {
    getResponse = () => json(404, { error: 'Thesis not found' })
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    expect(await screen.findByText('Noch keine Investment-These')).toBeInTheDocument()
  })

  it('USER gets no edit button', async () => {
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    expect(screen.queryByRole('button', { name: 'These bearbeiten' })).not.toBeInTheDocument()
  })

  it('renders text literally with line breaks kept (never as HTML)', async () => {
    getResponse = () => json(200, thesis({ whyInteresting: '<script>alert(1)</script>\nZeile 2' }))
    const { container } = render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    const el = await screen.findByText(/alert\(1\)/)
    expect(el.textContent).toBe('<script>alert(1)</script>\nZeile 2')
    expect(el).toHaveStyle({ whiteSpace: 'pre-wrap' })
    expect(container.querySelector('script')).toBeNull()
  })

  it('ADMIN edits prefilled form and saves with the loaded version', async () => {
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    expect(screen.getByLabelText('Warum interessant')).toHaveValue('Starker Burggraben')
    expect(screen.getByLabelText('Status')).toHaveValue('WATCHING')
    expect(screen.getByLabelText('Begründung')).toHaveValue('Zu teuer')
    fireEvent.change(screen.getByLabelText('Warum interessant'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toEqual({
      whyInteresting: 'Neu', expectedDevelopment: 'Wachstum', exitCriteria: 'Moat bricht weg',
      status: 'WATCHING', statusReason: 'Zu teuer', version: 3,
    })
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument())
  })

  it('clearing the status also clears the reason', async () => {
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: '' } })
    expect(screen.getByLabelText('Begründung')).toHaveValue('')
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toMatchObject({ status: null, statusReason: '' })
  })

  it('creating sends version null', async () => {
    getResponse = () => json(404, { error: 'Thesis not found' })
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Noch keine Investment-These')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Warum interessant'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toEqual({
      whyInteresting: 'Neu', expectedDevelopment: '', exitCriteria: '', status: null, statusReason: '', version: null,
    })
  })

  it('disables the status select with a hint when holding', async () => {
    getResponse = () => json(200, thesis({ holding: true }))
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Halte ich')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    expect(screen.getByLabelText('Status')).toBeDisabled()
    expect(screen.getByText('Status gilt nicht, solange die Position gehalten wird')).toBeInTheDocument()
  })

  it('409 shows the conflict message and reloads', async () => {
    putResponse = () => json(409, { error: 'Conflict, please reload' })
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    getResponse = () => json(200, thesis({ whyInteresting: 'Von jemand anderem', version: 4 }))
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Die These wurde inzwischen geändert, bitte neu laden')
    await waitFor(() => expect(screen.getByLabelText('Warum interessant')).toHaveValue('Von jemand anderem'))
  })

  it('422 shows the backend message', async () => {
    putResponse = () => json(422, { error: 'Thesis text too long' })
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Thesis text too long')
  })

  it('Abbrechen discards changes', async () => {
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Warum interessant'), { target: { value: 'Verworfen' } })
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(screen.queryByLabelText('Warum interessant')).not.toBeInTheDocument()
    expect(screen.getByText('Starker Burggraben')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'These bearbeiten' }))
    expect(screen.getByLabelText('Warum interessant')).toHaveValue('Starker Burggraben')
    expect(putBodies).toHaveLength(0)
  })
})
