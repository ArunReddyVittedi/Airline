// One place that knows how to talk to the backend.
//
// Every call goes through request(), so the token header, the JSON parsing and the error
// shape are decided once. Components get either data or an Error with a readable message,
// and never have to think about response.ok.

const TOKEN_KEY = 'airline.token'
const USER_KEY = 'airline.user'

export function saveSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function currentToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function currentUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    // Storage was corrupted or written by an older version of the app. Treat it as signed
    // out rather than letting a parse error take down the whole page.
    return null
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = currentToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401) {
    // The token expired while the tab was open. Clearing it here means the next render
    // sends them to the login page instead of showing empty lists with no explanation.
    clearSession()
    throw new Error('Your session has expired. Please sign in again.')
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new Error(data?.detail || data?.error || 'Something went wrong.')
  }

  return data
}

export const api = {
  register: (payload) => request('/api/auth/register', { method: 'POST', body: payload }),
  login: (payload) => request('/api/auth/login', { method: 'POST', body: payload }),

  searchFlights: (params) => {
    const query = new URLSearchParams(
      Object.entries(params).filter(([, value]) => value),
    )
    return request(`/api/flights?${query}`)
  },
  naturalSearch: (question) =>
    request('/api/flights/search/natural', { method: 'POST', body: { question } }),
  destinations: (origin = 'BOM') => request(`/api/flights/destinations?origin=${origin}`),
  disruptedFlights: () => request('/api/flights/disrupted'),

  myBookings: () => request('/api/bookings'),
  book: (flightNumber, seatNumber) =>
    request('/api/bookings', { method: 'POST', body: { flightNumber, seatNumber } }),
  refundQuote: (pnr) => request(`/api/bookings/${pnr}/refund-quote`),
  cancel: (pnr) => request(`/api/bookings/${pnr}/cancel`, { method: 'POST' }),
  rebook: (pnr, flightNumber) =>
    request(`/api/bookings/${pnr}/rebook?flightNumber=${flightNumber}`, { method: 'POST' }),

  ask: (question) => request('/api/assistant/ask', { method: 'POST', body: { question } }),
  handleDisruption: (pnr) => request(`/api/disruption/${pnr}`, { method: 'POST' }),
  planTrip: (payload) => request("/api/trip-planner", { method: "POST", body: payload }),

  myTickets: () => request('/api/tickets'),
  createTicket: (payload) => request('/api/tickets', { method: 'POST', body: payload }),

  adminAsk: (question) => request('/api/admin/ask', { method: 'POST', body: { question } }),
  adminTickets: () => request('/api/admin/tickets'),
  adminReindex: () => request('/api/admin/knowledge/reindex', { method: 'POST' }),
  setFlightStatus: (flightNumber, status, delayMinutes = 0) =>
    request(
      `/api/admin/flights/${flightNumber}/status?status=${status}&delayMinutes=${delayMinutes}`,
      { method: 'POST' },
    ),
}

// Streaming is not a fetch-and-parse, so it does not go through request().
//
// The backend sends server sent events, and each one arrives as a "data:" line. Reading the
// body as a stream and pushing each chunk to onToken is what makes the answer appear a word
// at a time instead of all at once after seven seconds.
export async function askStreaming(question, onToken) {
  const response = await fetch('/api/assistant/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${currentToken()}`,
    },
    body: JSON.stringify({ question }),
  })

  if (!response.ok || !response.body) {
    throw new Error('Could not reach the assistant.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // Events are separated by a blank line, and a chunk can end mid event, so whatever is
    // after the last separator stays in the buffer for the next read.
    const events = buffer.split('\n\n')
    buffer = events.pop() ?? ''

    for (const event of events) {
      for (const line of event.split('\n')) {
        if (line.startsWith('data:')) onToken(line.slice(5))
      }
    }
  }
}
