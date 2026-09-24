import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api.js'
import HeroPlane from './HeroPlane.jsx'
import Scenery from './Scenery.jsx'
import { Money } from './Ui.jsx'

const AUTOPLAY_MS = 6000
const MAX_SLIDES = 4

/**
 * The rotating banner on the home page.
 *
 * Every slide is a destination we genuinely fly to, with the lowest fare actually on sale
 * fetched from the API. That matters more than it sounds: a banner is a promise, and a
 * beautiful slide advertising a route with no seats is the fastest way to make a site feel
 * fake. If the schedule changes, the banner changes with it, because there is nothing here
 * that was typed in by hand.
 *
 * The behaviour is the fiddly part of any carousel, and most of it is about not annoying
 * people: it pauses on hover and on focus so a slide cannot move out from under a click, it
 * stops entirely for anyone who has asked their system to reduce motion, and off screen
 * slides are taken out of the tab order so a keyboard user is never sent to a link they
 * cannot see.
 */
export default function DestinationHero({ origin = 'BOM', onPick }) {
  const [destinations, setDestinations] = useState([])
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const timer = useRef(null)

  useEffect(() => {
    let live = true

    api
      .destinations(origin)
      .then((rows) => {
        if (!live) return
        setDestinations(rows.slice(0, MAX_SLIDES))
      })
      .catch(() => {
        // The banner is decoration. The search box below it is the real page, so a failure
        // here must not stop anyone booking a flight.
      })

    return () => {
      live = false
    }
  }, [origin])

  const count = destinations.length

  const go = useCallback(
    (next) => setIndex((i) => (count ? (next + count) % count : 0)),
    [count],
  )

  useEffect(() => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (paused || reduced || count < 2) return undefined

    timer.current = setTimeout(() => go(index + 1), AUTOPLAY_MS)
    return () => clearTimeout(timer.current)
  }, [index, paused, go, count])

  // Hold the space while the fares load rather than letting the page jump when they arrive.
  if (count === 0) {
    return (
      <section className="hero hero-loading" aria-hidden="true">
        <Scenery airportCode="BOM" variant="hero" />
        <div className="hero-wash" />
        {/* The aircraft still flies while the fares load, and keeps flying if they never
            arrive. Without it a backend that is down leaves a still, empty banner that
            reads as a broken page rather than a waiting one. */}
        <HeroPlane />
      </section>
    )
  }

  return (
    <section
      className="hero"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
      aria-roledescription="carousel"
      aria-label="Where we fly"
    >
      <div className="hero-track" style={{ transform: `translateX(-${index * 100}%)` }}>
        {destinations.map((destination, i) => (
          <article
            className={`hero-slide ${i === index ? "is-active" : ""}`}
            key={destination.code}
            aria-hidden={i !== index}
          >
            <Scenery airportCode={destination.code} variant="hero" />

            {/* A dark wash over the artwork. Without it, white text on a bright sunset is
                unreadable at exactly the moment somebody is trying to read the fare. */}
            <div className="hero-wash" />

            <div className="hero-copy" onClick={() => onPick?.(destination)} role="presentation">
              <span className="hero-eyebrow">
                {destination.flightsAvailable} flights over the next three weeks
              </span>

              <h1>{destination.city}</h1>

              <p className="hero-fare">
                from <Money amount={destination.fromFare} />
                <span className="hero-fare-note">one way, all taxes included</span>
              </p>

              {/* No button. The search panel overlaps the lower third of the banner, so a
                  call to action here was physically behind it. The whole slide is the
                  affordance instead: clicking it fills the search panel in. */}
            </div>
          </article>
        ))}
      </div>

      {/* Above the artwork, under the copy. One aircraft for the whole banner rather than one
          per slide, so it keeps flying through a slide change instead of restarting. */}
      <HeroPlane />

      {count > 1 && (
        <>
          <button className="hero-arrow prev" onClick={() => go(index - 1)} aria-label="Previous destination">
            <Chevron dir="left" />
          </button>
          <button className="hero-arrow next" onClick={() => go(index + 1)} aria-label="Next destination">
            <Chevron dir="right" />
          </button>

          <div className="hero-dots">
            {destinations.map((destination, i) => (
              <button
                key={destination.code}
                className={`hero-dot ${i === index ? 'active' : ''}`}
                onClick={() => go(i)}
                aria-label={`Show ${destination.city}`}
                aria-current={i === index}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

/** Drawn, not typed. A text chevron inherits font quirks and never sits quite centred. */
function Chevron({ dir }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path
        d={dir === 'left' ? 'M15 5 8 12l7 7' : 'M9 5l7 7-7 7'}
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
