/**
 * The assistant's face.
 *
 * Drawn rather than an image, for the same reason as everything else in this project: it
 * stays sharp at any size, it needs no network, and it can take its colours from CSS. The
 * launcher renders it at 34px and the panel header at 26px from the same source.
 *
 * The three dimensional look is all gradients and one inset highlight. A flat robot reads as
 * a clip art icon; the volume is what makes it look like an object sitting on the page. Three
 * things do the work: a top lit gradient on the head, a darker band under the chin so the
 * head appears to sit forward of the body, and a soft white gloss across the top of the
 * visor, which is what a curved screen does to a light source.
 *
 * @param idPrefix gradients need unique ids, and this component is on the page twice.
 *                 Without a prefix the second instance reuses the first one's definitions,
 *                 which in some browsers silently renders it flat.
 */
export default function RobotMark({ size = 34, idPrefix = 'robot', blinking = true }) {
  const id = (name) => `${idPrefix}-${name}`

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      className="robot-mark"
      aria-hidden="true"
    >
      <defs>
        {/* Lit from above, so the top of the head is the brightest part. */}
        <linearGradient id={id('shell')} x1="0.3" y1="0" x2="0.7" y2="1">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="45%" stopColor="#e8eef5" />
          <stop offset="100%" stopColor="#b8c6d6" />
        </linearGradient>

        {/* The visor is darker than the shell and glossier, which is what separates a screen
            from a painted panel. */}
        <linearGradient id={id('visor')} x1="0.2" y1="0" x2="0.8" y2="1">
          <stop offset="0%" stopColor="#1b3a5c" />
          <stop offset="60%" stopColor="#0d2540" />
          <stop offset="100%" stopColor="#0a1c31" />
        </linearGradient>

        <linearGradient id={id('gloss')} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#ffffff" stopOpacity="0.4" />
          <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
        </linearGradient>

        <radialGradient id={id('eye')}>
          <stop offset="0%" stopColor="#bfe9ff" />
          <stop offset="55%" stopColor="#4fc3f7" />
          <stop offset="100%" stopColor="#1e88c7" />
        </radialGradient>
      </defs>

      {/* Antenna, behind the head so the ball appears to sit on top of it. */}
      <rect x="30.6" y="4" width="2.8" height="8" rx="1.4" fill="#9fb0c2" />
      <circle className="robot-antenna" cx="32" cy="4.4" r="3.4" fill="#ffb057" />

      {/* Ear pods. Drawn before the head, so the head overlaps them slightly and they read
          as being on the far side rather than stuck on the front. */}
      <rect x="6" y="27" width="7" height="14" rx="3.5" fill="#9fb0c2" />
      <rect x="51" y="27" width="7" height="14" rx="3.5" fill="#9fb0c2" />

      {/* Head */}
      <rect x="11" y="14" width="42" height="36" rx="12" fill={`url(#${id('shell')})`} />

      {/* The band under the chin. One rectangle, and it does more for the sense of depth
          than anything else here. */}
      <path
        d="M13 42 Q 32 52 51 42 L 51 44 Q 32 54 13 44 Z"
        fill="#8f9fb1"
        opacity="0.5"
      />

      {/* Visor */}
      <rect x="17" y="21" width="30" height="20" rx="9" fill={`url(#${id('visor')})`} />

      <g className={blinking ? 'robot-eyes' : undefined}>
        <circle cx="25.5" cy="31" r="3.6" fill={`url(#${id('eye')})`} />
        <circle cx="38.5" cy="31" r="3.6" fill={`url(#${id('eye')})`} />
        {/* A single specular dot in each eye. Cheap, and it is the difference between eyes
            and two blue circles. */}
        <circle cx="24.4" cy="29.8" r="1.1" fill="#ffffff" opacity="0.9" />
        <circle cx="37.4" cy="29.8" r="1.1" fill="#ffffff" opacity="0.9" />
      </g>

      {/* Gloss across the top of the visor. */}
      <path
        d="M19 24 Q 32 19 45 24 L 45 29 Q 32 25 19 29 Z"
        fill={`url(#${id('gloss')})`}
      />

      {/* Shoulders, just enough to imply a body under the head. */}
      <path
        d="M18 50 Q 32 58 46 50 L 48 56 Q 32 62 16 56 Z"
        fill="#c9d4e0"
      />
    </svg>
  )
}
