import { useState } from 'react'

export function DeepResearchPlaceholder() {
  const [expanded, setExpanded] = useState(false)

  return (
    <div>
      <button type="button" onClick={() => setExpanded(!expanded)}>
        Run deep research
      </button>
      {expanded && (
        <p>
          Planned for the future: trigger a targeted deep research run for a selected stock —
          potentially via an LLM API or a trading API. Currently, research is done manually via
          Claude Code.
        </p>
      )}
    </div>
  )
}
