import { useState } from 'react'
import { api } from '../api.js'
import { Notice, Trace } from '../components/Ui.jsx'

/**
 * Trip-planning UI for the fixed research, flight-advice, and itinerary sequence.
 */
export default function TripPlanner() {
  const [form, setForm] = useState({
    originCity: 'Mumbai',
    destinationCity: 'Goa',
    days: 3,
    interests: 'beaches, seafood, not too much driving',
  })

  const [plan, setPlan] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  function set(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    setPlan(null)
    setBusy(true)

    try {
      setPlan(await api.planTrip({ ...form, days: Number(form.days) }))
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1 className="page-title">Trip planner</h1>
      <p className="page-lead">
        Destination guides come from retrieval, the weather from the ops server over MCP, and
        the flights from the database. The itinerary is written from all three.
      </p>

      <Notice kind="error">{error}</Notice>

      <div className="card">
        <form onSubmit={submit}>
          <div className="row">
            <div className="field">
              <label>From</label>
              <input value={form.originCity} onChange={(e) => set('originCity', e.target.value)} required />
            </div>
            <div className="field">
              <label>To</label>
              <select
                value={form.destinationCity}
                onChange={(e) => set('destinationCity', e.target.value)}
              >
                {/* Limited to the destinations the knowledge base actually covers. Offering
                    a city with no guide would make the research agent say it has nothing,
                    which is honest but a poor first impression. */}
                <option value="Goa">Goa</option>
                <option value="Srinagar">Srinagar</option>
                <option value="Bengaluru">Bengaluru</option>
              </select>
            </div>
            <div className="field" style={{ maxWidth: 110 }}>
              <label>Days</label>
              <input
                type="number"
                min="1"
                max="7"
                value={form.days}
                onChange={(e) => set('days', e.target.value)}
              />
            </div>
          </div>

          <div className="field" style={{ marginTop: 12 }}>
            <label>What do you enjoy?</label>
            <input value={form.interests} onChange={(e) => set('interests', e.target.value)} />
          </div>

          <button style={{ marginTop: 14 }} disabled={busy}>
            {busy ? 'Three agents are working...' : 'Plan my trip'}
          </button>
        </form>
      </div>

      {plan && (
        <>
          <div className="grid">
            <div className="card">
              <h3>About {plan.destination}</h3>
              <p className="small" style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                {plan.summary}
              </p>
            </div>

            <div className="card">
              <h3>Getting there</h3>
              <p className="small" style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                {plan.flightAdvice || <span className="muted">No flight advice returned.</span>}
              </p>
            </div>
          </div>

          {plan.weatherNote && <Notice>{plan.weatherNote}</Notice>}

          <div className="card">
            <h3>Your itinerary</h3>
            {plan.days.map((day) => (
              <div className="day" key={day.day}>
                <h4>
                  Day {day.day}: {day.title}
                </h4>
                <ul>
                  {day.activities.map((activity, index) => (
                    <li key={index}>{activity}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <Trace steps={plan.trace} />
        </>
      )}
    </div>
  )
}
