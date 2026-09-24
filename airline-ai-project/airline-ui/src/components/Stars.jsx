/**
 * The star field in the upper sky.
 *
 * Every scene in this banner is set at dusk, which is the one time of day where a sky can
 * carry both a warm horizon and stars overhead. They sit in the top of the frame only and
 * fade out well before the bright band near the sun, because a star drawn over a sunset is
 * the detail that makes a night sky look pasted on.
 *
 * The positions come from a seeded generator rather than Math.random. That is deliberate:
 * random positions are re-rolled on every render, so the whole sky would silently rearrange
 * itself each time the carousel advanced. Same seed, same sky, every time.
 */

/**
 * A tiny deterministic generator. Not a good random number generator and it does not need
 * to be. It needs to be repeatable and to look unpatterned, which this manages in one line.
 */
function seeded(seed) {
  let value = seed
  return () => {
    value = (value * 1103515245 + 12345) & 0x7fffffff
    return value / 0x7fffffff
  }
}

/** Built once at module load, so the sky is identical for every slide and every render. */
const STARS = buildStars()

function buildStars() {
  const random = seeded(20260908)
  const stars = []

  for (let i = 0; i < 46; i++) {
    // Weighted towards the top. Squaring a 0..1 value bunches the results near zero, which
    // puts most of the stars high in the frame and thins them out towards the horizon, the
    // way a real sky looks when the sun has only just gone.
    const depth = random() ** 2

    stars.push({
      x: random() * 1200,
      y: 6 + depth * 250,
      r: 0.9 + random() * 1.5,
      // The brighter stars are the higher ones, which reinforces the same effect.
      base: 0.35 + (1 - depth) * 0.5,
      // Spread over the twinkle duration so they never blink in unison. A sky that pulses
      // together looks like a string of fairy lights.
      delay: random() * 4.2,
      duration: 2.6 + random() * 2.4,
    })
  }

  return stars
}

/**
 * The four brightest, hand placed.
 *
 * These get a cross shaped glint rather than just a dot. Four of them is the right number:
 * one is an accident, a dozen turns the sky into glitter.
 */
const GLINTS = [
  { x: 205, y: 62, s: 1.15, delay: 0.4 },
  { x: 520, y: 34, s: 0.9, delay: 2.1 },
  { x: 762, y: 86, s: 1, delay: 1.2 },
  { x: 1088, y: 48, s: 0.8, delay: 3.3 },
]

export default function Stars() {
  return (
    <g className="stars">
      {STARS.map((star, i) => (
        <circle
          key={i}
          className="star"
          cx={star.x}
          cy={star.y}
          r={star.r}
          fill="#ffffff"
          style={{
            // Passed as custom properties so one keyframe rule covers every star and the
            // stylesheet does not need forty six copies of the same animation.
            '--star-base': star.base,
            animationDelay: `${star.delay}s`,
            animationDuration: `${star.duration}s`,
          }}
        />
      ))}

      {GLINTS.map((glint, i) => (
        <g
          key={`glint-${i}`}
          className="star-glint"
          transform={`translate(${glint.x} ${glint.y}) scale(${glint.s})`}
          style={{ animationDelay: `${glint.delay}s` }}
        >
          {/* A soft halo, then a cross. The halo is what reads as brightness; the cross on
              its own looks like a plus sign. */}
          <circle r="7" fill="#ffffff" opacity="0.14" />
          <circle r="1.9" fill="#ffffff" />
          <path
            d="M 0 -9 L 0.9 -1 L 0 0 L -0.9 -1 Z M 0 9 L 0.9 1 L 0 0 L -0.9 1 Z
               M -9 0 L -1 0.9 L 0 0 L -1 -0.9 Z M 9 0 L 1 0.9 L 0 0 L 1 -0.9 Z"
            fill="#ffffff"
            opacity="0.85"
          />
        </g>
      ))}
    </g>
  )
}
