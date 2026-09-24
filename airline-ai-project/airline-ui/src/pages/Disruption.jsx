import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Badge, Notice, Trace, When } from '../components/Ui.jsx'

/**
 * The disruption desk, and the page to demonstrate first.
 *
 * One click sends a PNR to a supervisor agent. The supervisor asks the situation agent what
 * happened, and depending on the answer consults the rebooking and compensation agents, then
 * has a fourth agent write the passenger's message. Each column below is a different agent,
 * and the trace at the bottom shows the order the supervisor chose.
 *
 * Run it against a healthy booking as well. The supervisor should find no disruption and
 * stop after one agent, which is the more interesting result of the two: it is proof the
 * delegation is a real decision and not a script.
 */
export default function Disruption() {
  const [bookings, setBookings] = useState([])
  const [outcome, setOutcome] = useState(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busyPnr, setBusyPnr] = useState('')
  const [moving, setMoving] = useState(false)

  useEffect(() => {
    api.myBookings().then(setBookings).catch((problem) => setError(problem.message))
  }, [])

  async function handle(pnr) {
    setError('')
    setOutcome(null)
    setBusyPnr(pnr)

    try {
      setOutcome(await api.handleDisruption(pnr))
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusyPnr('')
    }
  }

  async function acceptRebooking(pnr, flightNumber) {
    setError('')
    setMoving(true)

    try {
      const moved = await api.rebook(pnr, flightNumber)
      setOutcome(null)
      setBookings(await api.myBookings())
      // In the page, not a browser alert. An alert blocks everything, looks nothing like
      // the rest of the site, and is the one thing that instantly makes an app feel unfinished.
      setMessage(
        `You are moved to ${moved.flightNumber}. Your new booking reference is ${moved.pnr}, ` +
          `seat ${moved.seatNumber}.`,
      )
    } catch (problem) {
      setError(problem.message)
    } finally {
      setMoving(false)
    }
  }

  // Pulled out of the rebooking text so the accept button has a flight to act on. A regex
  // over agent output is not elegant, and the alternative was making the rebooking agent
  // return JSON, which measurably worsened its explanations.
  const suggestedFlight = outcome?.rebookingAdvice?.match(/\bTL\d{3}\b/)?.[0]

  return (
    <div className="page">
      <h1 className="page-title">Disruption desk</h1>
      <p className="page-lead">
        Four agents behind one button. A supervisor decides which of them to consult, and each
        one reads what the last one found.
      </p>

      <Notice kind="error">{error}</Notice>
      <Notice>{message}</Notice>

      <div className="card">
        <h3>Your bookings</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>PNR</th>
                <th>Flight</th>
                <th>Route</th>
                <th>Departs</th>
                <th>Flight status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.pnr}>
                  <td>
                    <strong>{booking.pnr}</strong>
                  </td>
                  <td>{booking.flightNumber}</td>
                  <td>
                    {booking.origin} to {booking.destination}
                  </td>
                  <td>
                    <When iso={booking.departureTime} />
                  </td>
                  <td>
                    <Badge status={booking.flightStatus} />
                  </td>
                  <td>
                    <button onClick={() => handle(booking.pnr)} disabled={busyPnr !== ''}>
                      {busyPnr === booking.pnr ? 'Agents working...' : 'Ask the desk'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {bookings.length === 0 && <p className="muted small">No bookings on this account.</p>}
      </div>

      {outcome && (
        <>
          <div className="card">
            <h3>Message for you</h3>
            <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{outcome.messageToPassenger}</p>

            {suggestedFlight && (
              <div className="row" style={{ marginTop: 16 }}>
                <button
                  onClick={() => acceptRebooking(outcome.pnr, suggestedFlight)}
                  disabled={moving}
                >
                  {moving ? 'Moving you...' : `Move me to ${suggestedFlight}`}
                </button>
              </div>
            )}
          </div>

          <div className="grid">
            <div className="card">
              <h3>What the situation agent found</h3>
              <p className="small" style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                {outcome.situation || <span className="muted">Not consulted.</span>}
              </p>
            </div>

            <div className="card">
              <h3>What the rebooking agent said</h3>
              <p className="small" style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                {outcome.rebookingAdvice || (
                  <span className="muted">
                    Not consulted. The supervisor decided there was nothing to rebook.
                  </span>
                )}
              </p>
            </div>

            <div className="card">
              <h3>What the compensation agent decided</h3>
              <p className="small" style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                {outcome.compensationAdvice || (
                  <span className="muted">
                    Not consulted. Nothing is owed on a flight that is operating normally.
                  </span>
                )}
              </p>
            </div>
          </div>

          <Trace steps={outcome.trace} />
        </>
      )}
    </div>
  )
}
