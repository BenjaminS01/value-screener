import { FormEvent, useState } from 'react'
import { Credentials } from '../api/portfolioApi'

interface LoginFormProps {
  onLogin: (credentials: Credentials) => void
}

export function LoginForm({ onLogin }: LoginFormProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onLogin({ username, password })
  }

  return (
    <form onSubmit={handleSubmit} aria-label="Anmelden">
      <label>
        Benutzername
        <input value={username} onChange={(e) => setUsername(e.target.value)} />
      </label>
      <label>
        Passwort
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      <button type="submit">Anmelden</button>
    </form>
  )
}
