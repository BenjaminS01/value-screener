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
    expect(screen.getByRole('heading', { name: 'Investment thesis' })).toBeInTheDocument()
    expect(screen.getByText('Personal view of the operator, not investment advice.')).toBeInTheDocument()
    expect(screen.getByText('Why interesting')).toBeInTheDocument()
    expect(screen.getByText('Expected development')).toBeInTheDocument()
    expect(screen.getByText('When to sell or drop')).toBeInTheDocument()
    expect(screen.getByText('Wachstum')).toBeInTheDocument()
    expect(screen.getByText('Moat bricht weg')).toBeInTheDocument()
    expect(screen.getByText('Watching')).toHaveClass('badge', 'badge--watching')
    expect(screen.getByText('Watching').closest('p')).toHaveTextContent('Zu teuer')
    expect(screen.getByText('Updated on 01 Nov 2026')).toBeInTheDocument()
  })

  it('shows Holding instead of the status when holding', async () => {
    getResponse = () => json(200, thesis({ holding: true }))
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    expect(await screen.findByText('Holding')).toHaveClass('badge', 'badge--holding')
    expect(screen.queryByText(/Watching/)).not.toBeInTheDocument()
  })

  it('shows No status when the status is null', async () => {
    getResponse = () => json(200, thesis({ status: null, statusReason: null }))
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    expect(await screen.findByText('No status')).toBeInTheDocument()
  })

  it('shows a hint when there is no thesis', async () => {
    getResponse = () => json(404, { error: 'Thesis not found' })
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    expect(await screen.findByText('No investment thesis yet')).toBeInTheDocument()
  })

  it('USER gets no edit button', async () => {
    render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    expect(screen.queryByRole('button', { name: 'Edit thesis' })).not.toBeInTheDocument()
  })

  it('renders text literally with line breaks kept (never as HTML)', async () => {
    getResponse = () => json(200, thesis({ whyInteresting: '<script>alert(1)</script>\nZeile 2' }))
    const { container } = render(<InvestmentThesisSection user={guest} isin={ISIN} />)
    const el = await screen.findByText(/alert\(1\)/)
    expect(el.textContent).toBe('<script>alert(1)</script>\nZeile 2')
    expect(el).toHaveClass('pre-wrap')
    expect(container.querySelector('script')).toBeNull()
  })

  it('ADMIN edits prefilled form and saves with the loaded version', async () => {
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    expect(screen.getByLabelText('Why interesting')).toHaveValue('Starker Burggraben')
    expect(screen.getByLabelText('Status')).toHaveValue('WATCHING')
    expect(screen.getByLabelText('Reason')).toHaveValue('Zu teuer')
    fireEvent.change(screen.getByLabelText('Why interesting'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toEqual({
      whyInteresting: 'Neu', expectedDevelopment: 'Wachstum', exitCriteria: 'Moat bricht weg',
      status: 'WATCHING', statusReason: 'Zu teuer', version: 3,
    })
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument())
  })

  it('clearing the status also clears the reason', async () => {
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: '' } })
    expect(screen.getByLabelText('Reason')).toHaveValue('')
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toMatchObject({ status: null, statusReason: '' })
  })

  it('creating sends version null', async () => {
    getResponse = () => json(404, { error: 'Thesis not found' })
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('No investment thesis yet')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    fireEvent.change(screen.getByLabelText('Why interesting'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toEqual({
      whyInteresting: 'Neu', expectedDevelopment: '', exitCriteria: '', status: null, statusReason: '', version: null,
    })
  })

  it('disables the status select with a hint when holding', async () => {
    getResponse = () => json(200, thesis({ holding: true }))
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Holding')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    expect(screen.getByLabelText('Status')).toBeDisabled()
    expect(screen.getByText('Status does not apply while the position is held')).toBeInTheDocument()
  })

  it('409 keeps the typed values, shows the conflict message and sends the fresh version next', async () => {
    putResponse = () => json(409, { error: 'Conflict, please reload' })
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    fireEvent.change(screen.getByLabelText('Why interesting'), { target: { value: 'Mein Text' } })
    getResponse = () => json(200, thesis({ whyInteresting: 'Von jemand anderem', version: 4 }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The thesis was changed in the meantime, please reload')
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(screen.getByLabelText('Why interesting')).toHaveValue('Mein Text')
    expect(screen.getByRole('alert')).toHaveTextContent('The thesis was changed in the meantime, please reload')
    putResponse = () => json(200, thesis({ version: 5 }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(putBodies).toHaveLength(2))
    expect(putBodies[1]).toMatchObject({ whyInteresting: 'Mein Text', version: 4 })
  })

  it('keeps the stored status and reason when holding and only a text field is edited', async () => {
    getResponse = () => json(200, thesis({ holding: true, status: 'WATCHING', statusReason: 'Bewertung zu hoch' }))
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Holding')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    fireEvent.change(screen.getByLabelText('Why interesting'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(putBodies).toHaveLength(1))
    expect(putBodies[0]).toMatchObject({
      whyInteresting: 'Neu', status: 'WATCHING', statusReason: 'Bewertung zu hoch',
    })
  })

  it('422 shows the backend message', async () => {
    putResponse = () => json(422, { error: 'Thesis text too long' })
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Thesis text too long')
  })

  it('Cancel discards changes', async () => {
    render(<InvestmentThesisSection user={admin} isin={ISIN} />)
    await screen.findByText('Starker Burggraben')
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    fireEvent.change(screen.getByLabelText('Why interesting'), { target: { value: 'Verworfen' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByLabelText('Why interesting')).not.toBeInTheDocument()
    expect(screen.getByText('Starker Burggraben')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Edit thesis' }))
    expect(screen.getByLabelText('Why interesting')).toHaveValue('Starker Burggraben')
    expect(putBodies).toHaveLength(0)
  })
})
