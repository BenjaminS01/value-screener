import { useState } from 'react'
import { Footer } from './components/Footer'
import { ImpressumPage } from './pages/ImpressumPage'
import { PortfolioPage } from './pages/PortfolioPage'

type View = 'portfolio' | 'impressum'

function App() {
  const [view, setView] = useState<View>('portfolio')

  return (
    <div>
      <header>
        <h1>Value Screener</h1>
        <nav>
          <button onClick={() => setView('portfolio')}>Portfolio</button>
          <button onClick={() => setView('impressum')}>Impressum</button>
        </nav>
      </header>
      {view === 'portfolio' ? <PortfolioPage /> : <ImpressumPage />}
      <Footer />
    </div>
  )
}

export default App
