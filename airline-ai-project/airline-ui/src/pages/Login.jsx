import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, saveSession } from '../api.js'
import { Notice } from '../components/Ui.jsx'

export default function Login({ onSignedIn }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: 'ramesh@example.com', password: 'telusko123', fullName: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  function set(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    setBusy(true)

    try {
      const result =
        mode === 'login'
          ? await api.login({ email: form.email, password: form.password })
          : await api.register(form)

      saveSession(result.token, result)
      onSignedIn(result)
      navigate('/trips')
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page auth">
      <div className="card">
        <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
        <p className="muted small" style={{ marginTop: 0 }}>
          {mode === 'login'
            ? 'The seeded passengers are ramesh@example.com and priya@example.com, and admin@telusko.com for the ops desk. The password is telusko123.'
            : 'A new account starts on the BLUE tier with no bookings.'}
        </p>

        <Notice kind="error">{error}</Notice>

        <form className="stack" onSubmit={submit}>
          {mode === 'register' && (
            <div className="field">
              <label>Full name</label>
              <input value={form.fullName} onChange={(e) => set('fullName', e.target.value)} required />
            </div>
          )}

          <div className="field">
            <label>Email</label>
            <input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} required />
          </div>

          <div className="field">
            <label>Password</label>
            <input
              type="password"
              value={form.password}
              onChange={(e) => set('password', e.target.value)}
              required
            />
          </div>

          <button disabled={busy}>
            {busy ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <p className="small muted" style={{ marginBottom: 0 }}>
          {mode === 'login' ? 'No account yet? ' : 'Already registered? '}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault()
              setMode(mode === 'login' ? 'register' : 'login')
              setError('')
            }}
          >
            {mode === 'login' ? 'Create one' : 'Sign in'}
          </a>
        </p>
      </div>
    </div>
  )
}
