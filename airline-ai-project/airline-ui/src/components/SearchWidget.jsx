import { useState } from 'react'

const CITIES = ['Mumbai', 'Delhi', 'Goa', 'Bengaluru', 'Chennai', 'Kolkata', 'Srinagar', 'Hyderabad']

/**
 * The search panel that sits over the banner, the way every airline site does it.
 *
 * Two tabs, and the second one is the interesting half of this whole application. The first
 * is an ordinary form that runs a database query. The second sends a sentence to a model,
 * which reads it into the same parameters and runs the same query. Identical results from
 * both, which is the honest picture of what a language model adds to a feature like this: a
 * better front door, not a different answer.
 *
 * Presenting them as tabs rather than as two boxes side by side was deliberate. Side by side
 * looked like a demo. Tabs look like a product, and the comparison is still one click away.
 */
export default function SearchWidget({ onSearch, onAsk, busy, initial }) {
  const [tab, setTab] = useState('form')

  const [form, setForm] = useState({
    origin: initial?.origin || 'Mumbai',
    destination: initial?.destination || 'Goa',
    date: initial?.date || isoDaysFromNow(3),
    cabin: 'ANY',
    timeOfDay: 'any',
  })

  const [sentence, setSentence] = useState('cheapest morning flight to Goa in three days')

  function set(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }))
  }

  /** Swaps the two cities. A tiny thing, and every airline site has it. */
  function swap() {
    setForm((previous) => ({ ...previous, origin: previous.destination, destination: previous.origin }))
  }

  return (
    <div className="search-card">
      <div className="search-tabs" role="tablist">
        <button
          role="tab"
          aria-selected={tab === 'form'}
          className={tab === 'form' ? 'active' : ''}
          onClick={() => setTab('form')}
        >
          Search flights
        </button>
        <button
          role="tab"
          aria-selected={tab === 'ask'}
          className={tab === 'ask' ? 'active' : ''}
          onClick={() => setTab('ask')}
        >
          Ask in your own words
        </button>
      </div>

      {tab === 'form' ? (
        <form
          className="search-body"
          onSubmit={(e) => {
            e.preventDefault()
            onSearch(form)
          }}
        >
          <div className="search-grid">
            <label className="search-field">
              <span>From</span>
              <input
                list="city-list"
                value={form.origin}
                onChange={(e) => set('origin', e.target.value)}
                required
              />
            </label>

            <button type="button" className="search-swap" onClick={swap} aria-label="Swap cities">
              <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <path
                  d="M7 7h11l-3-3M17 17H6l3 3"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </button>

            <label className="search-field">
              <span>To</span>
              <input
                list="city-list"
                value={form.destination}
                onChange={(e) => set('destination', e.target.value)}
                required
              />
            </label>

            <label className="search-field">
              <span>Departure</span>
              <input
                type="date"
                value={form.date}
                onChange={(e) => set('date', e.target.value)}
                // Nobody can book yesterday, and letting them try only produces an empty
                // result they have to work out for themselves.
                min={isoDaysFromNow(0)}
                required
              />
            </label>

            <label className="search-field">
              <span>Cabin</span>
              <select value={form.cabin} onChange={(e) => set('cabin', e.target.value)}>
                <option value="ANY">Any cabin</option>
                <option value="ECONOMY">Economy</option>
                <option value="PREMIUM_ECONOMY">Premium economy</option>
                <option value="BUSINESS">Business</option>
              </select>
            </label>

            <label className="search-field">
              <span>Time of day</span>
              <select value={form.timeOfDay} onChange={(e) => set('timeOfDay', e.target.value)}>
                <option value="any">Any time</option>
                <option value="morning">Morning</option>
                <option value="afternoon">Afternoon</option>
                <option value="evening">Evening</option>
              </select>
            </label>
          </div>

          <button className="btn-search" disabled={busy}>
            {busy ? 'Searching...' : 'Search flights'}
          </button>
        </form>
      ) : (
        <form
          className="search-body"
          onSubmit={(e) => {
            e.preventDefault()
            onAsk(sentence)
          }}
        >
          <label className="search-field wide">
            <span>Tell us where and when</span>
            <input
              value={sentence}
              onChange={(e) => setSentence(e.target.value)}
              placeholder="cheapest morning flight to Goa in three days"
              required
            />
          </label>

          <div className="search-examples">
            {[
              'business class to Delhi next Monday',
              'evening flight to Bengaluru on Friday',
              'fastest flight from Delhi to Srinagar next week',
            ].map((example) => (
              <button type="button" key={example} onClick={() => setSentence(example)}>
                {example}
              </button>
            ))}
          </div>

          <button className="btn-search" disabled={busy}>
            {busy ? 'Reading that...' : 'Find my flight'}
          </button>
        </form>
      )}

      {/* One datalist for both city inputs. Suggestions, not a closed list: the API accepts
          a city name or an IATA code, so somebody typing BOM should not be corrected. */}
      <datalist id="city-list">
        {CITIES.map((city) => (
          <option key={city} value={city} />
        ))}
      </datalist>
    </div>
  )
}

function isoDaysFromNow(days) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}
