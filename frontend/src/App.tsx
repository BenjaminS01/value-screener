import { useEffect, useState } from 'react'
import { setUnauthorizedHandler } from './api/apiClient'
import { AuthUser, fetchCurrentUser, logout } from './api/authApi'
import { Footer } from './components/Footer'
import { View } from './navigation'
import { DatenschutzPage } from './pages/DatenschutzPage'
import { ImpressumPage } from './pages/ImpressumPage'
import { LoginPage } from './pages/LoginPage'
import { PortfolioPage } from './pages/PortfolioPage'
import { ResearchDetailPage } from './pages/ResearchDetailPage'
import { ResearchOverviewPage } from './pages/ResearchOverviewPage'
import { StartPage } from './pages/StartPage'

const SESSION_EXPIRED_NOTE = 'Sitzung abgelaufen, bitte erneut anmelden'

function App() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [view, setView] = useState<View>({ name: 'home' })
  const [notice, setNotice] = useState<string | undefined>(undefined)

  useEffect(() => {
    // A 401 from any later API call means the session is gone: back to login.
    setUnauthorizedHandler(() => {
      setUser(null)
      setView({ name: 'home' })
      setNotice(SESSION_EXPIRED_NOTE)
    })
    let cancelled = false
    fetchCurrentUser()
      .then((u) => {
        if (!cancelled) setUser(u)
      })
      .catch(() => {
        if (!cancelled) setUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
      setUnauthorizedHandler(null)
    }
  }, [])

  function handleLoggedIn(loggedIn: AuthUser) {
    setNotice(undefined)
    setView({ name: 'home' })
    setUser(loggedIn)
  }

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // A 401 here already ran the global handler; other failures: session state is unknown,
      // so leave the app logged out locally either way.
    }
    setUser(null)
    setView({ name: 'home' })
  }

  if (loading) return <p>Lade …</p>

  const showLegal = view.name === 'impressum' || view.name === 'datenschutz'

  let content
  if (showLegal) {
    content = (
      <>
        {user === null && <button onClick={() => setView({ name: 'home' })}>Zur Anmeldung</button>}
        {view.name === 'impressum' ? <ImpressumPage /> : <DatenschutzPage />}
      </>
    )
  } else if (user === null) {
    content = <LoginPage onLoggedIn={handleLoggedIn} navigate={setView} notice={notice} />
  } else if (view.name === 'portfolio') {
    content = <PortfolioPage user={user} navigate={setView} />
  } else if (view.name === 'research') {
    content = <ResearchOverviewPage user={user} navigate={setView} />
  } else if (view.name === 'researchDetail') {
    content = <ResearchDetailPage user={user} isin={view.isin} navigate={setView} />
  } else {
    content = <StartPage />
  }

  return (
    <div>
      {user !== null && (
        <header>
          <button onClick={() => setView({ name: 'home' })}>Value Screener</button>
          <nav>
            <button onClick={() => setView({ name: 'portfolio' })}>Portfolio</button>
            <button onClick={() => setView({ name: 'research' })}>Research</button>
          </nav>
          <span>
            {user.username} ({user.role})
          </span>
          <button onClick={handleLogout}>Abmelden</button>
        </header>
      )}
      <main>{content}</main>
      <Footer navigate={setView} />
    </div>
  )
}

export default App
