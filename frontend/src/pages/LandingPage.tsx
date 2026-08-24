export function LandingPage() {
  return (
    <section>
      <h1>Value Investing, Backed by AI Research</h1>
      <p>
        Value Screener is a demo application for value-investing style stock research: it holds a
        public portfolio and a growing library of AI-assisted company research.
      </p>

      <h2>How it works</h2>
      <p>
        Today, research is done manually: the operator runs an AI research skill (via Claude Code)
        for a chosen stock, which gathers qualitative and financial findings. Once saved, that
        research is persisted as a snapshot and shown publicly on the Portfolio and Research pages.
      </p>

      <h2>Architecture</h2>
      <ul>
        <li>Backend: Java 21, Spring Boot 3, Spring AI</li>
        <li>Frontend: React, TypeScript</li>
        <li>Database: PostgreSQL</li>
      </ul>
      <p>Deployment on AWS App Runner is planned, but not yet live.</p>

      <h2>Roadmap</h2>
      <p>
        Planned: automated deep research via LLM and/or trading APIs, triggered directly from the
        UI — currently, research is done manually.
      </p>
    </section>
  )
}
