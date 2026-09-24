/**
 * Everything that moves in the sky: the stars, the clouds and the aeroplane.
 *
 * This is the one piece of pure decoration in the application, so it is worth being clear
 * about why it earns its place. An airline home page that is completely still reads as a
 * template. One slow aircraft tracking across the sky reads as an airline. The difference
 * costs about forty lines and no network requests.
 *
 * The restraint is the design. Twenty two seconds for a single crossing, a trail that draws
 * itself in behind the aircraft and fades, and clouds that take most of a minute to drift
 * past. Anything faster competes with the fare for attention, which is the opposite of what
 * a banner is for.
 *
 * It is drawn with SVG animateMotion rather than CSS offset-path. Both work; animateMotion
 * is declarative, needs no JavaScript ticking, and follows the same path element the trail
 * is drawn from, so the aircraft cannot drift off its own contrail.
 */
import Stars from './Stars.jsx'

export default function HeroPlane() {
  // The flight path. A shallow arc rather than a straight line, because a level crossing
  // looks like a sprite sliding across a background, and a curve reads as distance.
  //
  // It also has to stay in the top third of the sky, and that constraint was learned the
  // hard way. The first curve started at y 300 and flew the aircraft straight through the
  // destination name. The banner crops to roughly y 68 through 451 of this coordinate space
  // and the headline occupies about 235 to 350, so anything below 220 collides with the one
  // piece of text on the page that matters.
  const path = 'M -120 178 C 260 110, 700 80, 1340 44'

  return (
    <svg
      className="hero-plane"
      viewBox="0 0 1200 520"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      <defs>
        {/* The trail fades out behind the aircraft rather than ending abruptly. */}
        <linearGradient id="trail-fade" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stopColor="#ffffff" stopOpacity="0" />
          <stop offset="70%" stopColor="#ffffff" stopOpacity="0.5" />
          <stop offset="100%" stopColor="#ffffff" stopOpacity="0.9" />
        </linearGradient>

        {/* Without this the clouds are hard edged grey shapes, which is what they looked
            like on the first pass. Blurring them is the difference between a cloud and a
            smudge, and it costs one filter. */}
        <filter id="cloud-soften" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="9" />
        </filter>

        {/* A white aircraft disappears over the pale part of a sunset sky, which is exactly
            where the flight path takes it. A soft dark shadow underneath keeps the shape
            readable against both the dark top of the sky and the bright band near the sun. */}
        <filter id="plane-lift" x="-60%" y="-60%" width="220%" height="220%">
          <feDropShadow dx="0" dy="2.5" stdDeviation="3.5" floodColor="#04121f" floodOpacity="0.55" />
        </filter>
      </defs>

      {/* Behind everything else in the sky, so clouds pass in front of them and the
          aircraft flies over the top. */}
      <Stars />

      <Clouds />

      {/* The contrail. Drawn with a dash the length of the whole path, then offset, so it
          appears to be laid down behind the aircraft as it flies. */}
      <path
        className="contrail"
        d={path}
        fill="none"
        stroke="url(#trail-fade)"
        strokeWidth="2.5"
        strokeLinecap="round"
      />

      {/*
        The aircraft travels by CSS offset-path, not by SVG animateMotion.
        animateMotion is the more obvious tool and it was the first attempt. It also did not
        work: the contrail kept sweeping across, because that is a CSS dash animation, while
        the aircraft sat at the origin. SMIL and CSS are two separate animation engines and
        only one of them was running.

        offset-path puts both on the same engine, driven by the same duration, so they cannot
        drift apart. The path is handed over as a custom property rather than repeated in the
        stylesheet, which keeps one copy of the curve for the aircraft and its own trail.
      */}
      <g
        className="plane-body"
        filter="url(#plane-lift)"
        style={{ offsetPath: `path('${path}')` }}
      >
        <PlaneSilhouette />
      </g>
    </svg>
  )
}

/**
 * A small airliner from above and behind. Deliberately a silhouette: at this size any detail
 * turns to mud, and a shape reads instantly where a drawing does not.
 */
function PlaneSilhouette() {
  return (
    <g transform="scale(2.1)" fill="#ffffff" opacity="0.97">
      {/* fuselage */}
      <path d="M -16 0 L 12 0 Q 20 0 22 -1.6 L 22 1.6 Q 20 0 12 0 Z" />
      <ellipse cx="0" cy="0" rx="17" ry="2.6" />
      {/* main wings, swept back */}
      <path d="M 2 -1 L -12 -14 L -6 -14 L 6 -1 Z" />
      <path d="M 2 1 L -12 14 L -6 14 L 6 1 Z" />
      {/* tailplane */}
      <path d="M -14 -0.8 L -20 -7 L -17 -7 L -11 -0.8 Z" />
      <path d="M -14 0.8 L -20 7 L -17 7 L -11 0.8 Z" />
    </g>
  )
}

/**
 * Two layers of cloud at different speeds, looping without a seam.
 *
 * The first version animated each layer from one side to the other and started again, which
 * meant the sky emptied out and then the clouds reappeared in a block. The fix is the oldest
 * one in scrolling backgrounds: draw the set twice, one tile apart, and translate by exactly
 * one tile. As the first copy leaves on the right the second arrives from the left, and the
 * frame at the end of the cycle is pixel identical to the frame at the start, so there is
 * nothing to see when it restarts.
 *
 * The tile is the full 1200 of the viewBox. Any other number and the two copies would not
 * line up, which is the one thing that has to be exact here.
 *
 * The speed difference between the layers is still what reads as depth. It also means the
 * two patterns slide past each other and the combined sky never obviously repeats, even
 * though each layer does.
 */
function Clouds() {
  return (
    <>
      <g className="cloud-layer cloud-far" opacity="0.16" filter="url(#cloud-soften)">
        <CloudTile />
        <g transform="translate(1200 0)">
          <CloudTile />
        </g>
      </g>

      <g className="cloud-layer cloud-near" opacity="0.22" filter="url(#cloud-soften)">
        <CloudTile near />
        <g transform="translate(1200 0)">
          <CloudTile near />
        </g>
      </g>
    </>
  )
}

/**
 * One tile of cloud, 1200 wide.
 *
 * Positions are spread across the whole tile rather than clustered, because a gap wider than
 * about a third of the tile is long enough to notice as an empty sky.
 */
function CloudTile({ near = false }) {
  const clouds = near
    ? [
        { x: 90, y: 186, s: 2.1 },
        { x: 430, y: 158, s: 1.8 },
        { x: 720, y: 202, s: 2.4 },
        { x: 1010, y: 170, s: 1.9 },
      ]
    : [
        { x: 40, y: 92, s: 1.3 },
        { x: 260, y: 118, s: 1.5 },
        { x: 520, y: 70, s: 1.1 },
        { x: 780, y: 108, s: 1.6 },
        { x: 1030, y: 84, s: 1.2 },
      ]

  return (
    <>
      {clouds.map((cloud, i) => (
        <Cloud key={i} x={cloud.x} y={cloud.y} s={cloud.s} />
      ))}
    </>
  )
}

/** Three overlapping circles and a base. The oldest cloud in computer graphics, and it works. */
function Cloud({ x, y, s }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`} fill="#ffffff">
      <circle cx="0" cy="0" r="13" />
      <circle cx="15" cy="4" r="10" />
      <circle cx="-14" cy="5" r="9" />
      <rect x="-22" y="4" width="44" height="10" rx="5" />
    </g>
  )
}
