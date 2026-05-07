import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchAgentHistory, sendAgentChatMessage } from '../api/agent'
import { getErrorMessage } from '../api/http'
import { Sound } from '../component/icons'
import Topnav from '../component/topnav/topnav'
import { CHAT_SUGGESTIONS, EMPTY_STATE_PROMPTS } from '../data/agent-ui'
import styles from './chat.module.css'

const DEMO_USER_ID = 'demo-user'

function resolveChatError(error, fallbackMessage) {
  return getErrorMessage(error, fallbackMessage)
}

function ChatPage() {
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState([])
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [isSending, setIsSending] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const textareaRef = useRef(null)
  const streamRef = useRef(null)

  const hasMessages = messages.length > 0
  const ghostPrompt = hasMessages ? CHAT_SUGGESTIONS[0] : EMPTY_STATE_PROMPTS[0]

  useLayoutEffect(() => {
    const textarea = textareaRef.current
    if (!textarea) {
      return
    }

    textarea.style.height = '0px'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 220)}px`
  }, [hasMessages, input])

  useEffect(() => {
    if (!hasMessages || !streamRef.current) {
      return
    }

    streamRef.current.scrollTop = streamRef.current.scrollHeight
  }, [hasMessages, messages])

  useEffect(() => {
    let cancelled = false

    async function loadHistory() {
      try {
        const history = await fetchAgentHistory(DEMO_USER_ID, 20)
        if (cancelled) {
          return
        }

        const orderedHistory = history
          .map(normalizeMessage)
          .sort((left, right) => compareMessageTime(left.createdAt, right.createdAt))

        setMessages(orderedHistory)
        setErrorMessage('')
      } catch (error) {
        if (cancelled) {
          return
        }

        setErrorMessage(resolveChatError(error, 'Failed to load chat history.'))
      } finally {
        if (!cancelled) {
          setIsLoadingHistory(false)
        }
      }
    }

    loadHistory()

    return () => {
      cancelled = true
    }
  }, [])

  const submitMessage = async (messageText) => {
    const trimmed = messageText.trim()
    if (!trimmed || isSending) {
      return
    }

    const optimisticUserMessage = {
      id: `local-user-${Date.now()}`,
      role: 'USER',
      message: trimmed,
    }

    setMessages((current) => [...current, optimisticUserMessage])
    setInput('')
    setIsSending(true)
    setErrorMessage('')

    try {
      const response = await sendAgentChatMessage({
        userId: DEMO_USER_ID,
        message: trimmed,
        voiceInput: false,
      })

      const reply = normalizeMessage(response.reply)
      setMessages((current) => [...current, reply])

      if (Array.isArray(response.recommendedPlaylists) && response.recommendedPlaylists.length > 0) {
        window.dispatchEvent(new CustomEvent('agentmusic:playlists-updated'))
      }

      if (response.session) {
        window.dispatchEvent(new CustomEvent('agentmusic:playback-session-updated'))
      }
    } catch (error) {
      const message = resolveChatError(error, 'Failed to send the message.')
      setMessages((current) => [
        ...current,
        {
          id: `local-agent-error-${Date.now()}`,
          role: 'AGENT',
          message: `Agent request failed: ${message}`,
        },
      ])
      setErrorMessage(message)
    } finally {
      setIsSending(false)
    }
  }

  const handleKeyDown = (event) => {
    if ((event.key === 'Tab' || (event.key === 'Enter' && !event.shiftKey)) && !input.trim()) {
      event.preventDefault()
      acceptGhostPrompt()
      return
    }

    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submitMessage(input)
    }
  }

  const acceptGhostPrompt = () => {
    if (isSending || input.trim() || !ghostPrompt) {
      return
    }

    setInput(ghostPrompt)
    requestAnimationFrame(() => {
      textareaRef.current?.focus()
    })
  }

  return (
    <div className={styles.ChatPage}>
      <div className={styles.Bg}></div>
      <div className={styles.HoverBg}></div>

      <Topnav />

      <div className={styles.PageShell}>
        {!hasMessages && !isLoadingHistory ? (
          <section className={styles.EmptyFrame} data-testid="chat-empty-frame">
            <div className={styles.EmptyHero}>
              <div className={styles.EmptyEyebrowRow}>
                <p className={styles.Kicker}>AgentMusic</p>
                <span className={styles.ContextPill}>Conversation-first control</span>
              </div>
              <h1>Describe what you want to hear.</h1>
              <p className={styles.Subtitle}>
                Ask for a mood, a scene, an artist, or a playback change. AgentMusic will interpret the request,
                create the recommendation, and move playback with the same thread.
              </p>
              <div className={styles.EmptyActions}>
                <Link className={styles.SecondaryLink} to="/music">
                  Open Music Home
                </Link>
              </div>
            </div>

            <div className={styles.EmptyComposerBlock}>
              <Composer
                input={input}
                textareaRef={textareaRef}
                onInputChange={setInput}
                onKeyDown={handleKeyDown}
                onSubmit={submitMessage}
                onAcceptGhostPrompt={acceptGhostPrompt}
                ghostPrompt={ghostPrompt}
                centered
                disabled={isSending}
              />
              {errorMessage ? <p className={styles.StatusText}>{errorMessage}</p> : null}
            </div>
          </section>
        ) : (
          <section className={styles.ChatFrame} data-testid="chat-frame">
            <header className={styles.ChatHeader}>
              <div className={styles.ChatHeaderCopy}>
                <p className={styles.Kicker}>Active thread</p>
                <h1>Agent conversation</h1>
              </div>
              <div className={styles.ChatHeaderActions}>
                <button
                  className={styles.MinimalButton}
                  type="button"
                  onClick={() => {
                    setMessages([])
                    setErrorMessage('')
                  }}
                >
                  New thread
                </button>
                <Link className={styles.SecondaryLink} to="/music">
                  Open Music Home
                </Link>
              </div>
            </header>

            <div ref={streamRef} className={styles.ChatStream} data-testid="chat-stream">
              {isLoadingHistory ? (
                <p className={styles.StatusText}>Loading chat history...</p>
              ) : null}

              {messages.map((item) => {
                const isAgent = item.role === 'AGENT'
                return (
                  <article
                    key={item.id}
                    className={`${styles.MessageRow} ${isAgent ? styles.AgentRow : styles.UserRow}`.trim()}
                    data-testid="chat-message-row"
                  >
                    <div className={styles.MessageMeta}>
                      <span className={styles.MessageRole}>{isAgent ? 'Agent' : 'You'}</span>
                    </div>
                    <div
                      className={`${styles.MessageBubble} ${
                        isAgent ? styles.AgentBubble : styles.UserBubble
                      }`.trim()}
                    >
                      <p>{item.message}</p>
                    </div>
                  </article>
                )
              })}

              {isSending ? (
                <article
                  className={`${styles.MessageRow} ${styles.AgentRow}`.trim()}
                  data-testid="chat-message-pending"
                >
                  <div className={styles.MessageMeta}>
                    <span className={styles.MessageRole}>Agent</span>
                  </div>
                  <div className={`${styles.MessageBubble} ${styles.AgentBubble} ${styles.PendingBubble}`.trim()}>
                    <p>Agent is thinking...</p>
                  </div>
                </article>
              ) : null}

              {errorMessage ? <p className={styles.StatusText}>{errorMessage}</p> : null}
            </div>

            <div className={styles.BottomComposer}>
              <Composer
                input={input}
                textareaRef={textareaRef}
                onInputChange={setInput}
                onKeyDown={handleKeyDown}
                onSubmit={submitMessage}
                onAcceptGhostPrompt={acceptGhostPrompt}
                ghostPrompt={ghostPrompt}
                disabled={isSending}
              />
            </div>
          </section>
        )}
      </div>
    </div>
  )
}

function Composer({
  input,
  textareaRef,
  onInputChange,
  onKeyDown,
  onSubmit,
  onAcceptGhostPrompt,
  ghostPrompt,
  centered = false,
  disabled = false,
}) {
  const wrapperClassName = centered
    ? `${styles.Composer} ${styles.CenteredComposer}`
    : styles.Composer

  return (
    <div className={wrapperClassName}>
      <div className={styles.InputShell}>
        <div className={styles.InputStage}>
          {!input && !disabled ? (
            <button
              className={styles.GhostPrompt}
              type="button"
              onClick={onAcceptGhostPrompt}
              onFocus={onAcceptGhostPrompt}
            >
              <span className={styles.GhostPromptText}>{ghostPrompt}</span>
              <span className={styles.GhostPromptHint}>Tab or Enter to accept</span>
            </button>
          ) : null}
        <textarea
          ref={textareaRef}
          className={styles.ChatInput}
          rows={1}
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder=""
          disabled={disabled}
          tabIndex={input ? 0 : -1}
        />
        </div>
        <div className={styles.InputActions}>
          <TooltipIconButton tooltip="Voice input" disabled={disabled}>
            <Sound />
          </TooltipIconButton>
          <TooltipIconButton
            tooltip={disabled ? 'Sending' : 'Send'}
            filled
            disabled={disabled}
            onClick={() => onSubmit(input)}
          >
            <span className={styles.SendArrow}>-&gt;</span>
          </TooltipIconButton>
        </div>
      </div>
    </div>
  )
}

function TooltipIconButton({ children, tooltip, onClick, filled = false, disabled = false }) {
  return (
    <button
      className={`${styles.IconButton} ${filled ? styles.FilledIconButton : ''}`}
      type="button"
      onClick={onClick}
      disabled={disabled}
    >
      {children}
      <span className={styles.Tooltip}>{tooltip}</span>
    </button>
  )
}

function normalizeMessage(message) {
  return {
    id: message.id,
    role: typeof message.role === 'string' ? message.role : 'AGENT',
    message: message.message,
    createdAt: message.createdAt ?? null,
  }
}

function compareMessageTime(left, right) {
  const leftTime = left ? Date.parse(left) : 0
  const rightTime = right ? Date.parse(right) : 0
  return leftTime - rightTime
}

export default ChatPage
