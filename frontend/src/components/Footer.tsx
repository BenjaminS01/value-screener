import { Navigate } from '../navigation'

interface FooterProps {
  navigate: Navigate
}

export function Footer({ navigate }: FooterProps) {
  return (
    <footer className="app-footer">
      <div className="app-footer__inner">
        <p>
          This application is a technical demo project. All content shown is automatically generated
          analysis based on publicly available data and is not investment advice or a recommendation. It
          does not take individual investment goals or financial circumstances into account. Every
          decision is the user's own responsibility.
        </p>
        <nav>
          <button className="btn-link" onClick={() => navigate({ name: 'impressum' })}>
            Legal notice (Impressum)
          </button>
          <button className="btn-link" onClick={() => navigate({ name: 'datenschutz' })}>
            Privacy (Datenschutz)
          </button>
        </nav>
      </div>
    </footer>
  )
}
