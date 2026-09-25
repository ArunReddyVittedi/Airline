import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Badge, Notice, When } from '../components/Ui.jsx'

/**
 * Support-ticket submission and history. The assigned category, priority, and triage reason
 * make automated routing visible to the passenger.
 */
export default function Support() {
  const [tickets, setTickets] = useState([])
  const [form, setForm] = useState({ subject: '', message: '', pnr: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [justRaised, setJustRaised] = useState(null)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    try {
      setTickets(await api.myTickets())
    } catch (problem) {
      setError(problem.message)
    }
  }

  function set(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)

    try {
      const ticket = await api.createTicket(form)
      setJustRaised(ticket)
      setForm({ subject: '', message: '', pnr: '' })
      await load()
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1 className="page-title">Support</h1>
      <p className="page-lead">
        Raise a ticket and it is read and routed on the way in, so it reaches the right desk
        rather than the back of one queue.
      </p>

      <Notice kind="error">{error}</Notice>

      {justRaised && (
        <div className="card">
          <h3>Ticket raised</h3>
          <div className="row" style={{ alignItems: 'center', gap: 10 }}>
            <Badge status={justRaised.category} />
            <Badge status={justRaised.priority} />
            {!justRaised.aiTriaged && <span className="badge warn">not classified</span>}
          </div>
          <p className="small muted" style={{ marginBottom: 0, marginTop: 12 }}>
            {justRaised.triageReason}
          </p>
        </div>
      )}

      <div className="card">
        <h3>Raise a ticket</h3>
        <form onSubmit={submit}>
          <div className="row">
            <div className="field">
              <label>Subject</label>
              <input value={form.subject} onChange={(e) => set('subject', e.target.value)} required />
            </div>
            <div className="field" style={{ maxWidth: 140 }}>
              <label>PNR (optional)</label>
              <input value={form.pnr} onChange={(e) => set('pnr', e.target.value)} />
            </div>
          </div>

          <div className="field" style={{ marginTop: 12 }}>
            <label>What has happened?</label>
            <textarea value={form.message} onChange={(e) => set('message', e.target.value)} required />
          </div>

          <button style={{ marginTop: 14 }} disabled={busy}>
            {busy ? 'Raising...' : 'Raise ticket'}
          </button>
        </form>

        <p className="small muted" style={{ marginBottom: 0 }}>
          Try a wheelchair request for tomorrow, then a general compliment, and compare the
          priorities the agent gives them.
        </p>
      </div>

      {tickets.length > 0 && (
        <div className="card">
          <h3>Your tickets</h3>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Raised</th>
                  <th>Subject</th>
                  <th>PNR</th>
                  <th>Category</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>Why that routing</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                  <tr key={ticket.id}>
                    <td>
                      <When iso={ticket.createdAt} />
                    </td>
                    <td>{ticket.subject}</td>
                    <td className="muted">{ticket.pnr || '-'}</td>
                    <td>
                      <Badge status={ticket.category} />
                    </td>
                    <td>
                      <Badge status={ticket.priority} />
                    </td>
                    <td>
                      <Badge status={ticket.status} />
                    </td>
                    <td className="small muted" style={{ whiteSpace: 'normal', maxWidth: 320 }}>
                      {ticket.triageReason}
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
