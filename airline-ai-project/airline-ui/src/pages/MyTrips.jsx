import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { Badge, Money, Notice, When } from '../components/Ui.jsx'

export default function MyTrips() {
  const [bookings, setBookings] = useState([])
  const [quote, setQuote] = useState(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  /**
   * The PNR of a booking just made, handed over by the Book page.
   *
   * It was being passed and never read, so a passenger who had just paid for a seat landed
   * on a list of bookings with nothing confirming which one was theirs.
   */
  const justBooked = location.state?.justBooked

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setError('')
    try {
      setBookings(await api.myBookings())
    } catch (problem) {
      setError(problem.message)
    }
  }

  /**
   * Shows the fee before anything is cancelled.
   *
   * Two steps rather than one, deliberately. Nobody should discover a 25 percent
   * cancellation fee after the booking is already gone.
   */
  async function askForQuote(pnr) {
    setError('')
    setMessage('')
    try {
      setQuote(await api.refundQuote(pnr))
    } catch (problem) {
      setError(problem.message)
    }
  }

  async function confirmCancel() {
    setBusy(true)
    try {
      const result = await api.cancel(quote.pnr)
      setMessage(
        result.refundAmount > 0
          ? `Cancelled ${result.pnr}. A refund of ₹${result.refundAmount} is on its way.`
          : `Cancelled ${result.pnr}. ${result.reason}`,
      )
      setQuote(null)
      await load()
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  const disrupted = bookings.filter(
    (booking) => booking.flightStatus === 'CANCELLED' || booking.flightStatus === 'DELAYED',
  )

  return (
    <div className="page">
      <h1 className="page-title">My trips</h1>
      <p className="page-lead">Every booking on your account, newest first.</p>

      <Notice kind="error">{error}</Notice>
      <Notice>{message}</Notice>

      {justBooked && !message && (
        <Notice>
          Booking confirmed. Your reference is <strong>{justBooked}</strong>. Quote it at check in.
        </Notice>
      )}

      {disrupted.length > 0 && (
        <Notice kind="warn">
          {disrupted.length === 1 ? 'One of your flights has' : `${disrupted.length} of your flights have`}{' '}
          a problem. The disruption desk works out your options for you.{' '}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault()
              navigate('/disruption')
            }}
          >
            Open the disruption desk
          </a>
        </Notice>
      )}

      {quote && (
        <div className="card">
          <h3>Cancelling {quote.pnr}</h3>
          <div className="table-wrap">
            <table>
              <tbody>
                <tr>
                  <th>Paid</th>
                  <td>
                    <Money amount={quote.amountPaid} />
                  </td>
                </tr>
                <tr>
                  <th>Cancellation fee</th>
                  <td>
                    <Money amount={quote.cancellationFee} />
                  </td>
                </tr>
                <tr>
                  <th>You get back</th>
                  <td>
                    <strong>
                      <Money amount={quote.refundAmount} />
                    </strong>
                  </td>
                </tr>
                <tr>
                  <th>Hours to departure</th>
                  <td className="muted">{quote.hoursToDeparture}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <p className="small muted">{quote.reason}</p>

          <div className="row">
            <button className="danger" onClick={confirmCancel} disabled={busy}>
              {busy ? 'Cancelling...' : 'Confirm cancellation'}
            </button>
            <button className="quiet" onClick={() => setQuote(null)}>
              Keep the booking
            </button>
          </div>
        </div>
      )}

      {bookings.length === 0 ? (
        <div className="card">
          <p className="muted" style={{ margin: 0 }}>
            No bookings yet. Find a flight on the Book page.
          </p>
        </div>
      ) : (
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>PNR</th>
                  <th>Flight</th>
                  <th>Route</th>
                  <th>Departs</th>
                  <th>Seat</th>
                  <th>Paid</th>
                  <th>Booking</th>
                  <th>Flight status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {bookings.map((booking) => (
                  <tr key={booking.pnr} className={booking.pnr === justBooked ? 'row-new' : undefined}>
                    <td>
                      <strong>{booking.pnr}</strong>
                      {booking.rebookedToPnr && (
                        <div className="small muted">moved to {booking.rebookedToPnr}</div>
                      )}
                    </td>
                    <td>{booking.flightNumber}</td>
                    <td>
                      {booking.origin} to {booking.destination}
                    </td>
                    <td>
                      <When iso={booking.departureTime} />
                    </td>
                    <td className="muted">{booking.seatNumber}</td>
                    <td>
                      <Money amount={booking.amountPaid} />
                    </td>
                    <td>
                      <Badge status={booking.bookingStatus} />
                    </td>
                    <td>
                      <Badge status={booking.flightStatus} />
                    </td>
                    <td>
                      {booking.bookingStatus === 'CONFIRMED' && isUpcoming(booking) && (
                        <button className="quiet" onClick={() => askForQuote(booking.pnr)}>
                          Cancel
                        </button>
                      )}
                      {booking.bookingStatus === 'CONFIRMED' && !isUpcoming(booking) && (
                        <span className="muted small">Flown</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Returns whether the flight remains eligible for future-trip actions.
 */
function isUpcoming(booking) {
  return new Date(booking.departureTime) > new Date()
}
