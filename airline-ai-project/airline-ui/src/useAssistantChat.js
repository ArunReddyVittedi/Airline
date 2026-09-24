import { useEffect, useRef, useState } from 'react'
import { api, askStreaming } from './api.js'

const GREETING =
  'Hello. I can help with baggage rules, your bookings, refunds and check in. What do you need?'

/**
 * The conversation, shared by the full Assistant page and the floating widget.
 *
 * Both need the same things: a message list, a box to type in, and a send that can either
 * stream or wait. Only their surroundings differ, so the surroundings are all that lives in
 * the components. Two copies of this logic would drift within a week, and the streaming
 * reducer in particular is the sort of code you only want to get right once.
 */
export default function useAssistantChat() {
  const [messages, setMessages] = useState([{ who: 'bot', text: GREETING }])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  /** The blocking reply, kept so the page can show its trace. The widget ignores it. */
  const [lastReply, setLastReply] = useState(null)

  const logRef = useRef(null)

  // Keep the newest message in view. Without this the answer arrives below the fold and
  // the window looks like it did nothing at all.
  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight
  }, [messages])

  /**
   * @param streaming true to render token by token, false to wait and get the trace back.
   */
  async function send(streaming = true) {
    const text = question.trim()
    if (!text || busy) return

    setQuestion('')
    setError('')
    setLastReply(null)
    setMessages((previous) => [...previous, { who: 'me', text }])
    setBusy(true)

    try {
      if (streaming) {
        // Push an empty bubble first, then append each token to it as it arrives.
        setMessages((previous) => [...previous, { who: 'bot', text: '' }])

        await askStreaming(text, (token) => {
          setMessages((previous) => {
            const copy = [...previous]
            copy[copy.length - 1] = {
              who: 'bot',
              text: copy[copy.length - 1].text + token,
            }
            return copy
          })
        })
      } else {
        const reply = await api.ask(text)
        setMessages((previous) => [...previous, { who: 'bot', text: reply.answer }])
        setLastReply(reply)
      }
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  return { messages, question, setQuestion, busy, error, lastReply, logRef, send }
}
