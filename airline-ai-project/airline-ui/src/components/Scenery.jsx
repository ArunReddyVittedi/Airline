/**
 * Destination artwork, drawn rather than photographed.
 *
 * An airline home page wants a big picture of somewhere you want to be, and there is no photo
 * library in this project. The options were a stock photo URL, which breaks the moment the
 * network does and puts somebody else's licensing in our repo, or drawing the places.
 *
 * Drawing them turned out better than expected. Each scene is layered SVG with a per
 * destination palette, so a beach reads as a beach and Srinagar reads as mountains in snow,
 * and it renders instantly with nothing to load. It also scales to any tile size without
 * going soft, which a 1600px photo scaled into a 260px card does not.
 *
 * The same component draws the full width hero and the small destination cards. Only the
 * viewBox aspect changes.
 */

// One palette per scene. Sky first, ground last, so the gradients read top to bottom.
const PALETTES = {
  beach: {
    skyTop: '#1b4f8a',
    skyBottom: '#f0a868',
    sun: '#ffd9a0',
    water: '#0f6d8c',
    waterLight: '#2b98b5',
    sand: '#e8cfa8',
    accent: '#0b4a63',
  },
  mountains: {
    skyTop: '#123a63',
    skyBottom: '#c9d9e8',
    sun: '#ffeccc',
    water: '#4d7ea8',
    waterLight: '#7ba3c4',
    sand: '#e6edf4',
    accent: '#1c3552',
  },
  city: {
    skyTop: '#16233d',
    skyBottom: '#7a5a86',
    sun: '#ffd4b0',
    water: '#2b3b57',
    waterLight: '#41567a',
    sand: '#1e2a41',
    accent: '#0f1728',
  },
  metro: {
    skyTop: '#0f2a43',
    skyBottom: '#e07a4f',
    sun: '#ffc98a',
    water: '#144055',
    waterLight: '#256b82',
    sand: '#c8a97e',
    accent: '#0a2233',
  },
}

/**
 * Which scene each airport gets.
 *
 * Keyed by IATA code, because that is what the API returns and it is stable. A code we have
 * no scene for falls back to the city skyline, which is true of most airports.
 */
const SCENE_BY_AIRPORT = {
  GOI: 'beach',
  SXR: 'mountains',
  BLR: 'city',
  DEL: 'city',
  BOM: 'metro',
  MAA: 'beach',
  CCU: 'city',
  HYD: 'city',
}

export function sceneFor(airportCode) {
  return SCENE_BY_AIRPORT[airportCode] || 'city'
}

export default function Scenery({ airportCode, variant = 'hero', className = '' }) {
  const scene = sceneFor(airportCode)
  const palette = PALETTES[scene]

  // Both variants share one coordinate space, and CSS decides the box. The card used to
  // declare its own smaller viewBox, which meant the crop landed on the top left corner of
  // the drawing: every card was empty sky with a sun in it.
  //
  // The anchor is the difference that matters. A hero is wide and shallow, so it crops to
  // the middle and keeps the horizon. A card is nearly square, so it anchors to the bottom
  // and keeps the beach or the skyline rather than the sky above them.
  const gradientId = `sky-${scene}-${variant}`
  const anchor = variant === 'hero' ? 'xMidYMid slice' : 'xMidYMax slice'

  return (
    <svg
      className={`scenery ${className}`}
      viewBox="0 0 1200 520"
      preserveAspectRatio={anchor}
      role="img"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={palette.skyTop} />
          <stop offset="100%" stopColor={palette.skyBottom} />
        </linearGradient>

        {/* The light spilling out of a lit window. Drawn as a blurred copy of the pane
            underneath the sharp one, which is what turns a rectangle into a room. */}
        <filter id="window-bloom" x="-120%" y="-120%" width="340%" height="340%">
          <feGaussianBlur stdDeviation="4.5" />
        </filter>
      </defs>

      {/* Sky fills the whole box, whichever crop is in use. */}
      <rect x="-100" y="-200" width="1500" height="1000" fill={`url(#${gradientId})`} />

      {scene === 'beach' && <Beach p={palette} />}
      {scene === 'mountains' && <Mountains p={palette} />}
      {scene === 'city' && <City p={palette} />}
      {scene === 'metro' && <Metro p={palette} />}
    </svg>
  )
}

function Sun({ p, cx, cy, r }) {
  return (
    <>
      {/* A soft halo behind the disc, which is what stops it looking like a sticker. */}
      <circle cx={cx} cy={cy} r={r * 2.4} fill={p.sun} opacity="0.14" />
      <circle cx={cx} cy={cy} r={r * 1.5} fill={p.sun} opacity="0.2" />
      <circle cx={cx} cy={cy} r={r} fill={p.sun} opacity="0.92" />
    </>
  )
}

function Beach({ p }) {
  return (
    <>
      <Sun p={p} cx={905} cy={172} r={42} />

      {/* The horizon sits high on purpose. The search panel covers the lower third of the
          banner, so a beach drawn near the bottom of the frame is a beach nobody sees: the
          first version showed palm fronds with their trunks hidden behind the panel. */}
      <rect x="-100" y="318" width="1500" height="390" fill={p.water} />
      <path d="M-100 342 Q 300 330 700 346 T 1400 338 V 700 H -100 Z" fill={p.waterLight} opacity="0.5" />
      <path d="M-100 372 Q 400 360 820 378 T 1400 370 V 700 H -100 Z" fill={p.waterLight} opacity="0.38" />

      {/* Sand. Raising the horizon to clear the search panel was right, and the first attempt
          went too far: the water filled the frame and read as a wall of teal. This sits it
          back down so there is more sky than sea, which is how a beach photograph is framed. */}
      <path d="M-100 414 Q 400 396 900 418 T 1400 408 V 700 H -100 Z" fill={p.sand} />

      {/* Two palms, rooted in the sand and clear of the panel. Curved trunks, because a
          straight one looks like a lamp post. */}
      <Palm p={p} x={205} y={420} scale={0.9} />
      <Palm p={p} x={1005} y={428} scale={0.7} />
    </>
  )
}

function Palm({ p, x, y, scale }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      <path
        d="M0 0 C -6 -50 -14 -95 -30 -140"
        fill="none"
        stroke={p.accent}
        strokeWidth="9"
        strokeLinecap="round"
      />
      {[-165, -125, -85, -45, -5, 35].map((angle, i) => (
        <path
          key={i}
          d="M0 0 C 30 -22 66 -26 92 -8 C 62 -14 28 -8 0 0 Z"
          fill={p.accent}
          transform={`translate(-30 -140) rotate(${angle})`}
        />
      ))}
    </g>
  )
}

function Mountains({ p }) {
  return (
    <>
      <Sun p={p} cx={950} cy={150} r={44} />

      {/* Back range, pale, and a front range with snow caps over it. Distance is just opacity. */}
      <path d="M-100 400 L 180 210 L 420 380 L 640 190 L 900 400 L 1400 250 V 700 H -100 Z"
            fill={p.accent} opacity="0.35" />

      <path d="M-100 460 L 240 250 L 520 440 L 780 230 L 1080 460 L 1400 330 V 700 H -100 Z"
            fill={p.accent} opacity="0.75" />

      <path d="M240 250 L 300 288 L 268 302 L 240 288 L 212 300 L 180 288 Z" fill="#ffffff" opacity="0.9" />
      <path d="M780 230 L 848 274 L 812 290 L 780 274 L 748 288 L 712 274 Z" fill="#ffffff" opacity="0.9" />

      {/* The lake, which is the thing Srinagar is actually known for. */}
      <rect x="-100" y="460" width="1500" height="240" fill={p.water} />
      <path d="M-100 478 Q 400 470 900 480 T 1400 474 V 700 H -100 Z" fill={p.waterLight} opacity="0.5" />
    </>
  )
}

function City({ p }) {
  // Fixed rather than random, so a tile does not redraw itself differently on every render.
  const towers = [
    [80, 300, 78], [176, 240, 62], [252, 330, 90], [356, 200, 70], [440, 288, 84],
    [538, 250, 58], [610, 340, 96], [720, 210, 74], [810, 300, 64], [890, 260, 88],
    [992, 320, 70], [1076, 230, 82],
  ]

  return (
    <>
      <Sun p={p} cx={300} cy={140} r={40} />

      {towers.map(([x, top, w], i) => (
        <g key={i}>
          <rect x={x} y={top} width={w} height={700 - top} fill={p.accent} opacity={0.82} />
          <Windows x={x} top={top} w={w} seed={i * 37 + 11} />
        </g>
      ))}
    </>
  )
}

/**
 * The lit windows of one tower.
 *
 * The first version drew a flat rectangle per window at one of two opacities, and the result
 * looked exactly like what it was: a grid of squares. What makes a window read as a room
 * with a lamp on is not the pane, it is the light escaping around it, so each lit window is
 * drawn twice, once blurred and once sharp. The blurred copy is the spill.
 *
 * Three brightness tiers rather than lit and unlit, because a real tower has a few rooms
 * blazing, plenty on a dim lamp, and a lot of people already out. Unlit panes still get a
 * very faint cool fill, since glass at dusk reflects the sky rather than going black.
 */
function Windows({ x, top, w, seed, bottom = 560 }) {
  const rows = Math.floor((bottom - top) / 34)
  const cols = Math.max(1, Math.floor(w / 26))
  const random = seededRandom(seed)

  const panes = []

  for (let row = 0; row < rows; row++) {
    for (let col = 0; col < cols; col++) {
      const roll = random()

      panes.push({
        key: `${row}-${col}`,
        x: x + 8 + col * 26,
        y: top + 14 + row * 34,
        // bright, dim or dark
        tier: roll < 0.26 ? 'bright' : roll < 0.58 ? 'dim' : 'dark',
        // A handful of rooms switch on and off over a couple of minutes. Slow enough that
        // nobody watches it happen and quick enough that a second glance is different.
        flickers: roll > 0.985,
        delay: random() * 40,
      })
    }
  }

  const lit = panes.filter((pane) => pane.tier !== 'dark')

  return (
    <g>
      {/* The spill. Blurred and warm, drawn under the panes so the light appears to come
          through the glass rather than sit on top of it. */}
      <g filter="url(#window-bloom)" opacity="0.75">
        {lit.map((pane) => (
          <rect
            key={pane.key}
            x={pane.x}
            y={pane.y}
            width="11"
            height="15"
            fill={pane.tier === 'bright' ? '#ffd68a' : '#f0b96a'}
            opacity={pane.tier === 'bright' ? 0.85 : 0.4}
          />
        ))}
      </g>

      {panes.map((pane) => (
        <rect
          key={pane.key}
          className={pane.flickers ? 'window-flicker' : undefined}
          style={pane.flickers ? { animationDelay: `${pane.delay}s` } : undefined}
          x={pane.x}
          y={pane.y}
          width="11"
          height="15"
          fill={
            pane.tier === 'bright' ? '#ffe6b0' : pane.tier === 'dim' ? '#e8b874' : '#9fb6cc'
          }
          opacity={pane.tier === 'bright' ? 0.95 : pane.tier === 'dim' ? 0.55 : 0.09}
        />
      ))}
    </g>
  )
}

/** Same trick as the star field: repeatable, and unpatterned enough to look unplanned. */
function seededRandom(seed) {
  let value = (seed * 2654435761) & 0x7fffffff
  return () => {
    value = (value * 1103515245 + 12345) & 0x7fffffff
    return value / 0x7fffffff
  }
}

function Metro({ p }) {
  return (
    <>
      <Sun p={p} cx={860} cy={200} r={56} />

      {/* A bay, a promenade curve and low blocks. Mumbai from the sea wall. */}
      <rect x="-100" y="360" width="1500" height="340" fill={p.water} />
      <path d="M-100 388 Q 380 374 800 392 T 1400 382 V 700 H -100 Z" fill={p.waterLight} opacity="0.45" />

      {[[60, 300, 70], [148, 268, 54], [214, 320, 78], [304, 250, 62], [382, 290, 70],
        [468, 262, 50], [532, 310, 84]].map(([x, top, w], i) => (
        <g key={i}>
          <rect x={x} y={top} width={w} height={365 - top} fill={p.accent} opacity="0.85" />
          {/* These were bare silhouettes. Mumbai from the sea wall at dusk is a wall of
              lit windows, so they get the same treatment as the skyline scene. */}
          <Windows x={x} top={top} w={w} seed={i * 53 + 7} bottom={365} />
        </g>
      ))}

      {/* The sea link. Two towers and a slung cable, which is the recognisable part. */}
      <path d="M700 360 L 1200 360" stroke={p.accent} strokeWidth="8" opacity="0.9" />
      <path d="M760 360 L 760 250 M 1080 360 L 1080 250" stroke={p.accent} strokeWidth="7" opacity="0.9" />
      <path d="M760 250 Q 920 330 1080 250" fill="none" stroke={p.accent} strokeWidth="5" opacity="0.75" />

      <path d="M-100 452 Q 300 440 700 456 V 700 H -100 Z" fill={p.sand} opacity="0.85" />
    </>
  )
}
