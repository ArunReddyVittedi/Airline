// Shared presentational components used across pages.

export function Notice({ kind = 'info', children }) {
  if (!children) return null
  return <div className={`notice ${kind === 'info' ? '' : kind}`}>{children}</div>
}

export function Badge({ status }) {
  const good = ['SCHEDULED', 'CONFIRMED', 'CHECKED_IN', 'LANDED', 'RESOLVED', 'CLOSED']
  const bad = ['CANCELLED', 'URGENT']
  const warn = ['DELAYED', 'REBOOKED', 'REFUNDED', 'HIGH', 'OPEN', 'IN_PROGRESS']

  const kind = good.includes(status) ? 'good' : bad.includes(status) ? 'bad' : warn.includes(status) ? 'warn' : ''

  return <span className={`badge ${kind}`}>{String(status).replace(/_/g, ' ')}</span>
}

export function Money({ amount }) {
  if (amount === null || amount === undefined) return <span>-</span>
  // Indian grouping, no decimals. Fares here are whole rupees and ".00" on every row is
  // noise that makes a table harder to scan.
  return <span>{'₹'}{Number(amount).toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
}

export function When({ iso }) {
  if (!iso) return <span>-</span>
  const date = new Date(iso)
  return (
    <span>
      {date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
      {', '}
      {date.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: false })}
    </span>
  )
}

export function Duration({ minutes }) {
  if (!minutes) return <span>-</span>
  const hours = Math.floor(minutes / 60)
  return <span>{hours}h {minutes % 60}m</span>
}

// Displays agent/tool execution steps and retrieved sources for inspection.
export function Trace({ steps, sources }) {
  const hasSteps = steps && steps.length > 0
  const hasSources = sources && sources.length > 0

  if (!hasSteps && !hasSources) return null

  return (
    <div className="card">
      <h3>What happened behind the answer</h3>

      {hasSteps && (
        <div className="trace">
          {steps.map((step, index) => (
            <div className="trace-step" key={index}>
              <span className="name">{step.kind === 'agent' ? 'agent' : 'tool'} {step.name}</span>
              <span className="where">{step.ranOn}</span>
              <span className="detail">{step.detail}</span>
            </div>
          ))}
        </div>
      )}

      {!hasSteps && <p className="muted small">No tools were called. The answer came from the model alone.</p>}

      {hasSources && (
        <p className="muted small" style={{ marginBottom: 0, marginTop: 12 }}>
          Based on: {sources.join(', ')}
        </p>
      )}
    </div>
  )
}
