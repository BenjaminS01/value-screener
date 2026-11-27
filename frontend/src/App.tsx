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

const SESSION_EXPIRED_NOTE = 'Session expired, please log in again'

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

  if (loading) return <p className="app-main muted">Loading …</p>

  const showLegal = view.name === 'impressum' || view.name === 'datenschutz'

  let content
  if (showLegal) {
    content = (
      <>
        {user === null && (
          <button className="btn-link" onClick={() => setView({ name: 'home' })}>
            <span aria-hidden="true">← </span>Back to login
          </button>
        )}
        <div className="card">{view.name === 'impressum' ? <ImpressumPage /> : <DatenschutzPage />}</div>
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
    <div className="app">
      {user !== null && (
        <header className="app-header">
          <div className="app-header__inner">
            <button className="btn-link brand" onClick={() => setView({ name: 'home' })}>
              Value Screener
            </button>
            <nav>
              <button
                className="btn-link"
                aria-current={view.name === 'portfolio' ? 'page' : undefined}
                onClick={() => setView({ name: 'portfolio' })}
              >
                Portfolio
              </button>
              <button
                className="btn-link"
                aria-current={view.name === 'research' || view.name === 'researchDetail' ? 'page' : undefined}
                onClick={() => setView({ name: 'research' })}
              >
                Research
              </button>
            </nav>
            <span className="user-chip">
              {user.username} ({user.role})
            </span>
            <button onClick={handleLogout}>Log out</button>
          </div>
        </header>
      )}
      <main className="app-main">{content}</main>
      <Footer navigate={setView} />
    </div>
  )
}

export default App
