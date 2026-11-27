const REPO_URL = 'https://github.com/BenjaminS01/value-screener'

/** Short, public description of the project: shown before login and on the start page. Keep it general. */
export function ProjectOverview() {
  return (
    <div className="overview">
      <h1>Value Screener</h1>
      <p className="overview__lead">
        A research tool for value investing: checked key figures and assessments with sources, an investment thesis per
        stock, and the owner's portfolio. It does not trade and does not recommend.
      </p>
      <div className="overview__tiles">
        <div className="card">
          <h2>Portfolio</h2>
          <p>
            The operator's personal portfolio, shown as an example and not as investment advice.
            Signed-in users see which stocks it contains. Quantities and prices stay private.
          </p>
        </div>
        <div className="card">
          <h2>Research</h2>
          <p>
            18 fixed criteria per company, from valuation and balance sheet to moat and management, each with source and
            date. Gathered with an AI assistant, reviewed by the owner, old data flagged.
          </p>
        </div>
        <div className="card">
          <h2>Tech</h2>
          <p>
            Java 21 · Spring Boot · React · TypeScript · AWS serverless (Lambda, DynamoDB, CloudFront) · Terraform ·
            CI/CD with GitHub Actions
          </p>
        </div>
      </div>
      <p>
        <a href={REPO_URL} target="_blank" rel="noopener noreferrer">
          Source code on GitHub
        </a>
      </p>
    </div>
  )
}
