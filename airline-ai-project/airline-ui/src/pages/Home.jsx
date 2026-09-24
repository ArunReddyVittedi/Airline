import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api.js'
import DestinationHero from '../components/DestinationHero.jsx'
import SearchWidget from '../components/SearchWidget.jsx'
import Scenery from '../components/Scenery.jsx'
import { Badge, Duration, Money, Notice } from '../components/Ui.jsx'

/**
 * The landing page.
 *
 * Ordered the way an airline site is: the banner sells a destination, the search panel sits
 * over it because searching is what almost everyone came to do, and the fare tiles underneath
 * answer the other question people arrive with, which is "where can I go cheaply". The
 * assistant is mentioned once, as a way to get help, rather than being the headline. An
 * airline whose home page leads with its chatbot has the priorities backwards.
 */
export default function Home({ user }) {
  const [flights, setFlights] = useState([])
  const [interpreted, setInterpreted] = useState('')
  const [searched, setSearched] = useState(false)
  const [destinations, setDestinations] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [seed, setSeed] = useState(null)

  const resultsRef = useRef(null)
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  useEffect(() => {
    api.destinations('BOM').then(setDestinations).catch(() => {
      // The fare tiles are a nice extra. Search still works without them.
    })
  }, [])

  /**
   * A search in the URL runs itself.
   *
   * Every airline site does this, and for a good reason: a search result is the thing people
   * send each other. "Look, 2,900 to Goa on the 15th" is a link, and a link that lands on an
   * empty home page is useless. It also means the back button behaves, because a search is a
   * history entry rather than hidden component state.
   */
  useEffect(() => {
    const origin = params.get('from')
    const destination = params.get('to')
    const date = params.get('date')

    if (!origin || !destination || !date) return

    search(
      { origin, destination, date, cabin: params.get('cabin') || 'ANY', timeOfDay: params.get('when') || 'any' },
      { push: false },
    )
    // Deliberately runs once per URL change and not on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params])

  /** Brings the results into view. A list appearing below the fold reads as nothing happening. */
  function revealResults() {
    requestAnimationFrame(() => {
      resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  async function search(form, { push = true } = {}) {
    setError('')
    setInterpreted('')
    setBusy(true)

    try {
      setFlights(await api.searchFlights(form))
      setSearched(true)

      // Put the search in the URL so it can be shared and so back works. Skipped when the
      // search came from the URL in the first place, which would otherwise loop.
      if (push) {
        setParams(
          {
            from: form.origin,
            to: form.destination,
            date: form.date,
            ...(form.cabin && form.cabin !== 'ANY' ? { cabin: form.cabin } : {}),
            ...(form.timeOfDay && form.timeOfDay !== 'any' ? { when: form.timeOfDay } : {}),
          },
          { replace: true },
        )
      }

      revealResults()
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  async function ask(sentence) {
    setError('')
    setBusy(true)

    try {
      const result = await api.naturalSearch(sentence)
      setFlights(result.flights)
      // Echoing back what the model understood matters most when the result is empty:
      // otherwise there is no way to tell "no flights" from "we misread you".
      setInterpreted(result.interpretedAs)
      setSearched(true)
      revealResults()
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  /** Clicking a destination fills the search panel in rather than searching behind their back. */
  function pickDestination(destination) {
    setSeed({ origin: 'Mumbai', destination: destination.city, date: isoDaysFromNow(3) })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function book(flightNumber) {
    if (!user) {
      navigate('/login')
      return
    }

    setError('')
    try {
      const booking = await api.book(flightNumber, null)
      navigate('/trips', { state: { justBooked: booking.pnr } })
    } catch (problem) {
      setError(problem.message)
    }
  }

  return (
    <>
      <div className="hero-region">
        <DestinationHero origin="BOM" onPick={pickDestination} />

        <div className="shell search-overlay">
          {/* Keyed on the seed so picking a destination remounts the widget with the new
              cities in it. Simpler than syncing parent state into child state. */}
          <SearchWidget
            key={seed ? `${seed.origin}-${seed.destination}` : 'default'}
            initial={seed}
            onSearch={search}
            onAsk={ask}
            busy={busy}
          />
        </div>
      </div>

      <main className="shell">
        {error && <Notice kind="error">{error}</Notice>}

        {/* Rendered only once a search has run. An always present empty section still takes
            its section margin, which left a large unexplained gap above the fare tiles. */}
        {searched && (
          <section ref={resultsRef}>
            <>
              <div className="section-head">
                <h2>{flights.length === 0 ? 'No flights found' : `${flights.length} flights`}</h2>
                {interpreted && <p className="section-note">We read that as: {interpreted}</p>}
              </div>

              {flights.length === 0 ? (
                <Notice kind="warn">
                  Nothing on that route and date. The schedule runs for the next three weeks
                  from Mumbai to Goa, Delhi, Bengaluru, Chennai and Srinagar, and from Delhi
                  to Srinagar.
                </Notice>
              ) : (
                <ul className="flight-list">
                  {flights.map((flight) => (
                    <li className="flight-row" key={flight.flightNumber}>
                      <div className="flight-when">
                        <strong>{timeOf(flight.departureTime)}</strong>
                        <span>{flight.origin}</span>
                      </div>

                      <div className="flight-leg">
                        <Duration minutes={flight.durationMinutes} />
                        <div className="leg-line">
                          <span className="dot" />
                          <span className="line" />
                          <PlaneMark />
                        </div>
                        <span className="leg-note">Direct</span>
                      </div>

                      <div className="flight-when">
                        <strong>{timeOf(flight.arrivalTime)}</strong>
                        <span>{flight.destination}</span>
                      </div>

                      <div className="flight-meta">
                        <span className="flight-number">{flight.flightNumber}</span>
                        <span className="muted small">{flight.cabinClass.replace('_', ' ')}</span>
                        {flight.seatsAvailable <= 12 && (
                          <span className="badge warn">{flight.seatsAvailable} seats left</span>
                        )}
                        {flight.status !== 'SCHEDULED' && <Badge status={flight.status} />}
                      </div>

                      <div className="flight-buy">
                        <span className="fare">
                          <Money amount={flight.fare} />
                        </span>
                        <button onClick={() => book(flight.flightNumber)}>Select</button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </>
          </section>
        )}

        <section>
          <div className="section-head">
            <h2>Where we fly from Mumbai</h2>
            <p className="section-note">Lowest fare on sale over the next three weeks.</p>
          </div>

          <div className="dest-grid">
            {destinations.map((destination) => (
              <button
                className="dest-card"
                key={destination.code}
                onClick={() => pickDestination(destination)}
              >
                <div className="dest-art">
                  <Scenery airportCode={destination.code} variant="card" />
                </div>
                <div className="dest-body">
                  <div>
                    <h3>{destination.city}</h3>
                    <span className="muted small">{destination.code}</span>
                  </div>
                  <div className="dest-fare">
                    <span className="muted small">from</span>
                    <strong>
                      <Money amount={destination.fromFare} />
                    </strong>
                  </div>
                </div>
              </button>
            ))}
          </div>
        </section>

        <section className="promise-strip">
          <Promise
            title="Free changes up to 24 hours"
            body="Cancel or move your flight more than a day before departure and there is no fee at all."
          />
          <Promise
            title="Answers, not hold music"
            body="Our assistant reads your actual booking and the actual policy, so it can tell you what you are owed."
          />
          <Promise
            title="We sort disruption out"
            body="If we cancel, we find you another flight and refund the difference. You do not have to chase us."
          />
        </section>

        {!user && (
          <section className="signin-strip">
            <div>
              <h3>Already booked with us?</h3>
              <p className="muted">
                Sign in to see your trips, ask about your booking, and get help if a flight changes.
              </p>
            </div>
            <button onClick={() => navigate('/login')}>Sign in</button>
          </section>
        )}
      </main>
    </>
  )
}

function Promise({ title, body }) {
  return (
    <div className="promise">
      <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
        <path
          d="M20 6 9 17l-5-5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <div>
        <h4>{title}</h4>
        <p>{body}</p>
      </div>
    </div>
  )
}

function PlaneMark() {
  return (
    <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true" className="plane-mark">
      <path d="M2 12l19-7-7 19-3-8z" fill="currentColor" />
    </svg>
  )
}

function timeOf(iso) {
  if (!iso) return '--:--'
  return new Date(iso).toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function isoDaysFromNow(days) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}
