import { FormEvent, useState } from 'react'
import { ApiError } from '../api/apiClient'
import { AuthUser, login } from '../api/authApi'
import { Navigate } from '../navigation'

interface LoginPageProps {
  onLoggedIn: (user: AuthUser) => void
  navigate: Navigate
  notice?: string
}

function loginErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return 'Benutzername oder Passwort falsch'
    if (e.status === 429) {
      if (e.retryAfterSeconds === null) {
        return 'Zu viele Anmeldeversuche, bitte später erneut versuchen'
      }
      const minutes = Math.max(1, Math.ceil(e.retryAfterSeconds / 60))
      return `Zu viele Anmeldeversuche, bitte in ${minutes} Minuten erneut versuchen`
    }
  }
  return 'Anmeldung fehlgeschlagen'
}

export function LoginPage({ onLoggedIn, navigate, notice }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      const user = await login(username, password)
      onLoggedIn(user)
    } catch (e) {
      setError(loginErrorMessage(e))
      setPassword('')
      setPending(false)
    }
  }

  return (
    <section>
      <h1>Anmelden</h1>
      {notice && <p role="status">{notice}</p>}
      <form onSubmit={handleSubmit}>
        <label>
          Benutzername
          <input
            value={username}
            autoComplete="username"
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>
        <label>
          Passwort
          <input
            type="password"
            value={password}
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" disabled={pending}>
          Anmelden
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
      <nav>
        <button type="button" onClick={() => navigate({ name: 'impressum' })}>
          Impressum
        </button>
        <button type="button" onClick={() => navigate({ name: 'datenschutz' })}>
          Datenschutz
        </button>
      </nav>
    </section>
  )
}
