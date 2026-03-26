import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Sound } from '../component/icons'
import Topnav from '../component/topnav/topnav'
import { fetchAgentHistory, sendAgentChatMessage } from '../api/agent'
import styles from './chat.module.css'
import { CHAT_SUGGESTIONS, EMPTY_STATE_PROMPTS } from '../data/agent-ui'

const DEMO_USER_ID = 'demo-user'

function ChatPage() {
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState([])
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [isSending, setIsSending] = useState(false)
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
  }, [input, hasMessages])

  useEffect(() => {
    if (!hasMessages || !streamRef.current) {
      return
    }

    streamRef.current.scrollTop = streamRef.current.scrollHeight
  }, [messages, hasMessages])

  useEffect(() => {
    let cancelled = false

    async function loadHistory() {
      try {
        const history = await fetchAgentHistory(DEMO_USER_ID, 20)
        if (cancelled) {
          return
        }

        setMessages(history.map(normalizeMessage))
        setErrorMessage('')
      } catch (error) {
        if (cancelled) {
          return
        }

        setErrorMessage(error.message || '聊天历史加载失败。')
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
      setMessages((current) => [
        ...current,
        {
          id: `local-agent-error-${Date.now()}`,
          role: 'AGENT',
          message: `请求 Agent 失败：${error.message || '未知错误'}`,
        },
      ])
      setErrorMessage(error.message || '消息发送失败。')
    } finally {
      setIsSending(false)
    }
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submitMessage(input)
    }
  }

  return (
    <div className={styles.ChatPage}>
      <Topnav />

      <div className={styles.PageShell}>
        {!hasMessages && !isLoadingHistory ? (
          <section className={styles.EmptyState}>
            <div className={styles.EmptyContent}>
              <p className={styles.Kicker}>AgentMusic</p>
              <h1>你想听什么？</h1>
              <p className={styles.Subtitle}>
                直接描述你想听的风格、场景或播放需求，Agent 会先理解意图，再生成推荐和播放动作。
              </p>
              <div className={styles.EmptyActions}>
                <Link className={styles.SecondaryLink} to="/music">
                  打开音乐主界面
                </Link>
              </div>
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
              <div className={styles.PromptGrid}>
                {EMPTY_STATE_PROMPTS.map((prompt) => (
                  <button
                    key={prompt}
                    className={styles.PromptCard}
                    type="button"
                    onClick={() => setInput(prompt)}
                  >
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          </section>
        ) : (
          <section className={styles.ChatLayout}>
            <header className={styles.ChatHeader}>
              <div>
                <p className={styles.Kicker}>智能聊天</p>
                <h1>对话中</h1>
              </div>
              <div className={styles.ChatHeaderActions}>
                <button
                  className={styles.MinimalButton}
                  type="button"
                  onClick={() => setMessages([])}
                >
                  新建对话
                </button>
                <Link className={styles.SecondaryLink} to="/music">
                  切换到音乐主界面
                </Link>
              </div>
            </header>

            <div ref={streamRef} className={styles.ChatStream}>
              {isLoadingHistory ? (
                <p className={styles.StatusText}>正在加载聊天历史...</p>
              ) : null}
              {messages.map((item) => (
                <article
                  key={item.id}
                  className={`${styles.MessageBubble} ${
                    item.role === 'AGENT' ? styles.AgentBubble : styles.UserBubble
                  }`}
                >
                  <p>{item.message}</p>
                </article>
              ))}
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
              <div className={styles.SuggestionRow}>
                {CHAT_SUGGESTIONS.map((suggestion) => (
                  <button
                    key={suggestion}
                    className={styles.SuggestionChip}
                    type="button"
                    onClick={() => setInput(suggestion)}
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
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
        <textarea
          ref={textareaRef}
          className={styles.ChatInput}
          rows={1}
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder="给 AgentMusic 发送消息"
          disabled={disabled}
        />
        <div className={styles.InputActions}>
          <TooltipIconButton tooltip="点击进行语音输入或长按 Ctrl+M" disabled={disabled}>
            <Sound />
          </TooltipIconButton>
          <TooltipIconButton
            tooltip={disabled ? '发送中' : '发送'}
            filled
            disabled={disabled}
            onClick={() => onSubmit(input)}
          >
            <span className={styles.SendArrow}>↑</span>
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
  }
}

export default ChatPage
