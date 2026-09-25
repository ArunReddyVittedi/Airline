import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Badge, Notice, Trace, When } from '../components/Ui.jsx'

/**
 * Short labels and their full operations-analysis questions.
 */
const QUESTIONS = [
  { label: 'Revenue', ask: 'What did we make this week and how many bookings was that?' },
  { label: 'Busiest routes', ask: 'Which routes are busiest this month?' },
  { label: 'Disruption', ask: 'How many flights are cancelled right now?' },
  { label: 'Support queue', ask: 'What does the support queue look like?' },
]

/**
 * The ops desk. Admin only.
 *
 * The analytics agent has its own tools and none of the passenger ones, which is the whole
 * reason both AI services use EXPLICIT wiring. Under automatic wiring LangChain4j would give
 * every service every tool bean it can find by type, and the passenger assistant would
 * happily tell anyone who asked what the airline earned this week.
 */
export default function Admin() {
  const [question, setQuestion] = useState(QUESTIONS[0].ask)
  const [reply, setReply] = useState(null)
  const [tickets, setTickets] = useState([])
  const [flight, setFlight] = useState({ flightNumber: '', status: 'CANCELLED', delayMinutes: 180 })
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')

  useEffect(() => {
    api.adminTickets().then(setTickets).catch((problem) => setError(problem.message))
  }, [])

  async function ask(event) {
    event.preventDefault()
    setError('')
    setBusy('ask')

    try {
      setReply(await api.adminAsk(question))
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy('')
    }
  }

  async function setStatus(event) {
    event.preventDefault()
    setError('')
    setMessage('')
    setBusy('status')

    try {
      const updated = await api.setFlightStatus(
        flight.flightNumber,
        flight.status,
        Number(flight.delayMinutes),
      )
      setMessage(`${updated.flightNumber} is now ${updated.status}.`)
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy('')
    }
  }

  async function reindex() {
    setError('')
    setMessage('')
    setBusy('reindex')

    try {
      const result = await api.adminReindex()
      setMessage(result.message)
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy('')
    }
  }

  const notTriaged = tickets.filter((ticket) => !ticket.aiTriaged).length

  return (
    <div className="page">
      <h1 className="page-title">Operations</h1>
      <p className="page-lead">
        Numbers, disruption and the knowledge index. Every figure below comes from a tool
        running a real query, so the assistant cannot round or extrapolate.
      </p>

      <Notice kind="error">{error}</Notice>
      <Notice>{message}</Notice>

      <div className="card">
        <h3>Ask about the airline</h3>
        <form onSubmit={ask}>
          <div className="field">
            <label>Question</label>
            <input value={question} onChange={(e) => setQuestion(e.target.value)} required />
          </div>

          <div className="row" style={{ marginTop: 12 }}>
            <button disabled={busy === 'ask'}>{busy === 'ask' ? 'Asking...' : 'Ask'}</button>
            {QUESTIONS.map((preset) => (
              <button
                type="button"
                key={preset.label}
                className="quiet"
                onClick={() => setQuestion(preset.ask)}
                disabled={busy !== ''}
              >
                {preset.label}
              </button>
            ))}
          </div>
        </form>

        {reply && (
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0, marginTop: 16 }}>{reply.answer}</p>
        )}
      </div>

      {reply && <Trace steps={reply.trace} />}

      <div className="grid">
        <div className="card">
          <h3>Cancel or delay a flight</h3>
          <p className="small muted" style={{ marginTop: 0 }}>
            This is what gives the disruption agents something real to react to. Cancel a
            flight here, then open the disruption desk on a booking that was on it.
          </p>

          <form onSubmit={setStatus}>
            <div className="row">
              <div className="field">
                <label>Flight</label>
                <input
                  value={flight.flightNumber}
                  onChange={(e) => setFlight({ ...flight, flightNumber: e.target.value })}
                  placeholder="TL401"
                  required
                />
              </div>
              <div className="field">
                <label>Status</label>
                <select
                  value={flight.status}
                  onChange={(e) => setFlight({ ...flight, status: e.target.value })}
                >
                  <option value="CANCELLED">Cancelled</option>
                  <option value="DELAYED">Delayed</option>
                  <option value="SCHEDULED">Back to scheduled</option>
                </select>
              </div>
              {flight.status === 'DELAYED' && (
                <div className="field" style={{ maxWidth: 120 }}>
                  <label>Minutes</label>
                  <input
                    type="number"
                    value={flight.delayMinutes}
                    onChange={(e) => setFlight({ ...flight, delayMinutes: e.target.value })}
                  />
                </div>
              )}
            </div>

            <button className="secondary" style={{ marginTop: 12 }} disabled={busy === 'status'}>
              {busy === 'status' ? 'Updating...' : 'Update status'}
            </button>
          </form>
        </div>

        <div className="card">
          <h3>Knowledge index</h3>
          <p className="small muted" style={{ marginTop: 0 }}>
            The vectors are a copy of the policy text and do not update themselves. Reindex
            after editing an article, otherwise the assistant keeps quoting the old wording.
            This costs embedding calls, which is why it is a button and not automatic.
          </p>
          <button className="secondary" onClick={reindex} disabled={busy === 'reindex'}>
            {busy === 'reindex' ? 'Reindexing...' : 'Reindex the knowledge base'}
          </button>
        </div>
      </div>

      <div className="card">
        <h3>Open tickets</h3>

        {notTriaged > 0 && (
          <Notice kind="warn">
            {notTriaged} {notTriaged === 1 ? 'ticket was' : 'tickets were'} never classified, so
            they are sitting at the default category and priority. Worth reading by hand.
          </Notice>
        )}

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Raised</th>
                <th>Subject</th>
                <th>Category</th>
                <th>Priority</th>
                <th>Triaged</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <tr key={ticket.id}>
                  <td>
                    <When iso={ticket.createdAt} />
                  </td>
                  <td>{ticket.subject}</td>
                  <td>
                    <Badge status={ticket.category} />
                  </td>
                  <td>
                    <Badge status={ticket.priority} />
                  </td>
                  <td className="muted small">{ticket.aiTriaged ? 'by agent' : 'no'}</td>
                  <td className="small muted" style={{ whiteSpace: 'normal', maxWidth: 300 }}>
                    {ticket.triageReason}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {tickets.length === 0 && <p className="muted small">The queue is empty.</p>}
      </div>

      <div className="card">
        <h3>Monitoring</h3>
        <p className="small muted" style={{ marginTop: 0 }}>
          Prometheus scrapes /actuator/prometheus. Grafana is on port 3000 once the monitoring
          stack is up, and the dashboard shows feature latency, tokens, retrieval quality,
          agent handoffs and guardrail blocks.
        </p>
        <div className="row">
          <a href="/actuator/prometheus" target="_blank" rel="noreferrer">
            <button className="quiet">Raw metrics</button>
          </a>
          <a href="http://localhost:3000" target="_blank" rel="noreferrer">
            <button className="quiet">Open Grafana</button>
          </a>
        </div>
      </div>
    </div>
  )
}
