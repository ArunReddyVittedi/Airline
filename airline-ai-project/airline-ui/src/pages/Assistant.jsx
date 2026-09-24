import { useState } from 'react'
import useAssistantChat from '../useAssistantChat.js'
import { Notice, Trace } from '../components/Ui.jsx'

const SUGGESTIONS = [
  'How much checked baggage do I get in economy?',
  'What are my upcoming flights?',
  'My flight is cancelled. What am I owed?',
  'Can I take a power bank in my checked bag?',
  'How early should I get to Delhi airport?',
]

/**
 * The chat window, with a switch between streaming and blocking.
 *
 * Both are here because the difference is worth seeing. Streaming feels immediate and cannot
 * be checked by an output guardrail, since the first tokens are already on screen before the
 * last one is written. Blocking waits, and comes back with the trace, the token count and a
 * guardrail that has verified every flight number in the answer. That trade is real, and it
 * is not obvious until you watch both.
 */
export default function Assistant() {
  const [streaming, setStreaming] = useState(true)

  // Shared with the floating widget. Only the surroundings differ: this page adds the
  // streaming switch, the trace and the token count.
  const { messages, question, setQuestion, busy, error, lastReply, logRef, send } =
    useAssistantChat()

  return (
    <div className="page">
      <h1 className="page-title">Assistant</h1>
      <p className="page-lead">
        Retrieval for the policy questions, tools for anything about your real bookings, and
        memory so you do not have to repeat yourself.
      </p>

      <Notice kind="error">{error}</Notice>

      <div className="card chat">
        <div className="chat-log" ref={logRef}>
          {messages.map((message, index) => (
            <div className={`bubble ${message.who}`} key={index}>
              {message.text || (busy && index === messages.length - 1 ? 'Thinking...' : '')}
            </div>
          ))}
        </div>

        <form
          className="chat-form"
          onSubmit={(event) => {
            event.preventDefault()
            send(streaming)
          }}
        >
          <input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ask about baggage, your bookings, refunds..."
            disabled={busy}
          />
          <button disabled={busy || !question.trim()}>Send</button>
        </form>
      </div>

      <div className="card">
        <h3>Try one of these</h3>
        <div className="row">
          {SUGGESTIONS.map((suggestion) => (
            <button
              key={suggestion}
              className="quiet"
              onClick={() => setQuestion(suggestion)}
              disabled={busy}
            >
              {suggestion}
            </button>
          ))}
        </div>

        <label className="row small" style={{ marginTop: 16, alignItems: 'center', gap: 8 }}>
          <input
            type="checkbox"
            checked={streaming}
            onChange={(e) => setStreaming(e.target.checked)}
            style={{ width: 'auto' }}
          />
          <span className="muted">
            Stream the answer. Turn this off to see the trace, the token count and the output
            guardrail, which streaming cannot use.
          </span>
        </label>
      </div>

      {lastReply && (
        <>
          <Trace steps={lastReply.trace} sources={lastReply.sources} />
          <p className="muted small">{lastReply.totalTokens} tokens for that answer.</p>
        </>
      )}
    </div>
  )
}
