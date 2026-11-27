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
    if (e.status === 401) return 'Invalid username or password'
    if (e.status === 429) {
      if (e.retryAfterSeconds === null) {
        return 'Too many login attempts, please try again later'
      }
      const minutes = Math.max(1, Math.ceil(e.retryAfterSeconds / 60))
      return `Too many login attempts, please try again in ${minutes} minutes`
    }
  }
  return 'Login failed'
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
    <section className="card login">
      <h1>Log in</h1>
      {notice && <p role="status">{notice}</p>}
      <form onSubmit={handleSubmit}>
        <label>
          Username
          <input value={username} autoComplete="username" onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" disabled={pending}>
          Log in
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
      <nav>
        <button type="button" className="btn-link" onClick={() => navigate({ name: 'impressum' })}>
          Legal notice (Impressum)
        </button>
        <button type="button" className="btn-link" onClick={() => navigate({ name: 'datenschutz' })}>
          Privacy (Datenschutz)
        </button>
      </nav>
    </section>
  )
}
