import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchAgentHistory, sendAgentChatMessageStream } from '../api/agent'
import { getErrorMessage } from '../api/http'
import { Sound } from '../component/icons'
import Topnav from '../component/topnav/topnav'
import styles from './chat.module.css'

const DEMO_USER_ID = 'demo-user'
const INPUT_PLACEHOLDER = '想听什么？例如：推荐张雨生《发晕》，或来点90年代粤语歌'
const STREAM_CHAR_DELAY_MS = 10

function resolveChatError(error, fallbackMessage) {
  return getErrorMessage(error, fallbackMessage)
}

function wait(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

function ChatPage() {
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState([])
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [isSending, setIsSending] = useState(false)
  const [isStreamingReply, setIsStreamingReply] = useState(false)
  const [streamStatus, setStreamStatus] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const textareaRef = useRef(null)
  const streamRef = useRef(null)

  const hasMessages = messages.length > 0

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
    setStreamStatus('正在发送消息...')
    setErrorMessage('')

    try {
      const streamingReplyId = `local-agent-stream-${Date.now()}`
      let hasStreamedReply = false
      let deltaDrain = Promise.resolve()
      const response = await sendAgentChatMessageStream({
        userId: DEMO_USER_ID,
        message: trimmed,
        voiceInput: false,
      }, {
        onStatus: (message) => {
          if (message) {
            setStreamStatus(message)
          }
        },
        onDelta: (delta) => {
          if (!delta) {
            return
          }

          if (!hasStreamedReply) {
            hasStreamedReply = true
            setIsStreamingReply(true)
            setMessages((current) => [
              ...current,
              {
                id: streamingReplyId,
                role: 'AGENT',
                message: '',
                isStreaming: true,
              },
            ])
          }

          deltaDrain = deltaDrain.then(() => appendStreamDelta(streamingReplyId, delta))
        },
      })

      const reply = normalizeMessage(response.reply)
      if (hasStreamedReply) {
        await deltaDrain
        setMessages((current) =>
          current.map((message) =>
            message.id === streamingReplyId
              ? {
                  ...reply,
                  isStreaming: false,
                }
              : message
          )
        )
        setIsStreamingReply(false)
      } else {
        await streamAgentReply(reply)
      }
      setStreamStatus('')

      if (Array.isArray(response.recommendedPlaylists) && response.recommendedPlaylists.length > 0) {
        window.dispatchEvent(new CustomEvent('agentmusic:playlists-updated'))
      }

      if (response.session) {
        window.dispatchEvent(new CustomEvent('agentmusic:playback-session-updated'))
      }
    } catch (error) {
      setIsStreamingReply(false)
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
      setStreamStatus('')
    }
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submitMessage(input)
    }
  }

  const appendStreamDelta = async (streamingId, delta) => {
    for (const character of Array.from(delta)) {
      setMessages((current) =>
        current.map((message) =>
          message.id === streamingId
            ? {
                ...message,
                message: `${message.message}${character}`,
              }
            : message
        )
      )
      await wait(STREAM_CHAR_DELAY_MS)
    }
  }

  const streamAgentReply = async (reply) => {
    const fullMessage = reply.message ?? ''
    const streamingId = `${reply.id}-streaming`
    setIsStreamingReply(true)
    setMessages((current) => [
      ...current,
      {
        ...reply,
        id: streamingId,
        message: '',
        isStreaming: true,
      },
    ])

    for (const character of Array.from(fullMessage)) {
      setMessages((current) =>
        current.map((message) =>
          message.id === streamingId
            ? {
                ...message,
                message: `${message.message}${character}`,
              }
            : message
        )
      )
      await wait(STREAM_CHAR_DELAY_MS)
    }

    setMessages((current) =>
      current.map((message) =>
        message.id === streamingId
          ? {
              ...reply,
              isStreaming: false,
            }
          : message
      )
    )
    setIsStreamingReply(false)
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
                      <p>
                        {item.message}
                        {item.isStreaming ? <span className={styles.StreamCursor} aria-hidden="true" /> : null}
                      </p>
                    </div>
                  </article>
                )
              })}

              {isSending && !isStreamingReply ? (
                <article
                  className={`${styles.MessageRow} ${styles.AgentRow}`.trim()}
                  data-testid="chat-message-pending"
                >
                  <div className={styles.MessageMeta}>
                    <span className={styles.MessageRole}>Agent</span>
                  </div>
                  <div className={`${styles.MessageBubble} ${styles.AgentBubble} ${styles.PendingBubble}`.trim()}>
                    <p>{streamStatus || '正在理解你的需求并整理推荐...'}</p>
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
        <textarea
          ref={textareaRef}
          className={styles.ChatInput}
          rows={1}
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder={INPUT_PLACEHOLDER}
          disabled={disabled}
        />
        </div>
        <div className={styles.InputActions}>
          <TooltipIconButton tooltip="语音输入" disabled={disabled}>
            <Sound />
          </TooltipIconButton>
          <TooltipIconButton
            tooltip={disabled ? '发送中' : '发送'}
            filled
            disabled={disabled}
            onClick={() => onSubmit(input)}
          >
            <PaperPlaneIcon />
          </TooltipIconButton>
        </div>
      </div>
    </div>
  )
}

function PaperPlaneIcon() {
  return (
    <svg className={styles.SendIcon} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path
        d="M5.2 19.1 19.8 4.5 14.4 21l-3.2-7.1L4 10.7l15.8-6.2"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.9"
      />
    </svg>
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
    isStreaming: message.isStreaming ?? false,
  }
}

function compareMessageTime(left, right) {
  const leftTime = left ? Date.parse(left) : 0
  const rightTime = right ? Date.parse(right) : 0
  return leftTime - rightTime
}

export default ChatPage
