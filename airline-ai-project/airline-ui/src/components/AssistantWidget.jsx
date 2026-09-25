import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import useAssistantChat from '../useAssistantChat.js'
import RobotMark from './RobotMark.jsx'

const QUICK = [
  'What are my upcoming flights?',
  'Baggage allowance in economy?',
  'Can I get a refund?',
]

/**
 * Floating assistant for short questions without leaving the current page. It is available
 * only to signed-in users and hidden on the full Assistant page, which provides trace details.
 */
export default function AssistantWidget({ user }) {
  const [open, setOpen] = useState(false)
  const { messages, question, setQuestion, busy, error, logRef, send } = useAssistantChat()
  const location = useLocation()
  const navigate = useNavigate()

  // Close the panel with the standard Escape-key interaction.
  useEffect(() => {
    if (!open) return undefined

    const onKey = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  if (!user || location.pathname === '/assistant') {
    return null
  }

  return (
    <>
      {open && (
        <section className="widget-panel" role="dialog" aria-label="Travel assistant">
          <header className="widget-head">
            <RobotMark size={30} idPrefix="widget-head" />

            <div className="widget-title">
              <strong>Travel assistant</strong>
              <span>Baggage, bookings, refunds</span>
            </div>

            <button className="widget-x" onClick={() => setOpen(false)} aria-label="Close">
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path
                  d="M6 6l12 12M18 6L6 18"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                />
              </svg>
            </button>
          </header>

          <div className="widget-log" ref={logRef}>
            {messages.map((message, i) => (
              <div className={`bubble ${message.who}`} key={i}>
                {message.text || (busy && i === messages.length - 1 ? 'Thinking...' : '')}
              </div>
            ))}

            {error && <div className="notice error">{error}</div>}
          </div>

          {/* Only shown at the start. Once there is a conversation they are in the way. */}
          {messages.length === 1 && (
            <div className="widget-quick">
              {QUICK.map((text) => (
                <button key={text} onClick={() => setQuestion(text)} disabled={busy}>
                  {text}
                </button>
              ))}
            </div>
          )}

          <form
            className="widget-form"
            onSubmit={(event) => {
              event.preventDefault()
              // The panel always streams. It is a quick answer in a corner, and the trace
              // it would give up belongs on the full page.
              send(true)
            }}
          >
            <input
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask about your trip..."
              disabled={busy}
              autoFocus
            />
            <button disabled={busy || !question.trim()} aria-label="Send">
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path d="M2 21l21-9L2 3v7l15 2-15 2z" fill="currentColor" />
              </svg>
            </button>
          </form>

          <button className="widget-expand" onClick={() => navigate('/assistant')}>
            Open the full assistant, with the trace
          </button>
        </section>
      )}

      <button
        className={`widget-launcher ${open ? 'is-open' : ''}`}
        onClick={() => setOpen((was) => !was)}
        aria-expanded={open}
        aria-label={open ? 'Close the assistant' : 'Ask the travel assistant'}
      >
        {/* Two rings that pulse outwards, so the button catches the eye once without
            animating forever in the corner of somebody's screen. */}
        {!open && (
          <>
            <span className="launcher-ring" />
            <span className="launcher-ring delayed" />
          </>
        )}

        <RobotMark size={38} idPrefix="widget-launcher" />
      </button>
    </>
  )
}
