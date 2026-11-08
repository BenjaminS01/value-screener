import { Navigate } from '../navigation'

interface FooterProps {
  navigate: Navigate
}

export function Footer({ navigate }: FooterProps) {
  return (
    <footer>
      <p>
        Diese Anwendung ist ein technisches Demonstrationsprojekt. Alle dargestellten Inhalte sind
        automatisiert erzeugte Analysen auf Basis öffentlich zugänglicher Daten und stellen keine
        Anlageberatung oder Anlageempfehlung dar. Sie berücksichtigen keine individuellen
        Anlageziele oder finanziellen Verhältnisse. Jegliche Entscheidung liegt in der
        Verantwortung des Nutzers.
      </p>
      <nav>
        <button onClick={() => navigate({ name: 'impressum' })}>Impressum</button>
        <button onClick={() => navigate({ name: 'datenschutz' })}>Datenschutz</button>
      </nav>
    </footer>
  )
}
