import { useState } from 'react'
import { Footer } from './components/Footer'
import { ImpressumPage } from './pages/ImpressumPage'
import { LandingPage } from './pages/LandingPage'
import { PortfolioPage } from './pages/PortfolioPage'
import { ResearchLibraryPage } from './pages/ResearchLibraryPage'

type View = 'home' | 'portfolio' | 'research' | 'impressum'

function App() {
  const [view, setView] = useState<View>('home')

  return (
    <div>
      <header>
        <h1>Value Screener</h1>
        <nav>
          <button onClick={() => setView('home')}>Home</button>
          <button onClick={() => setView('portfolio')}>Portfolio</button>
          <button onClick={() => setView('research')}>Research</button>
          <button onClick={() => setView('impressum')}>Impressum</button>
        </nav>
      </header>
      {view === 'home' && <LandingPage />}
      {view === 'portfolio' && <PortfolioPage />}
      {view === 'research' && <ResearchLibraryPage />}
      {view === 'impressum' && <ImpressumPage />}
      <Footer />
    </div>
  )
}

export default App
