import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Sound } from '../component/icons'
import Topnav from '../component/topnav/topnav'
import styles from './chat.module.css'
import { CHAT_SUGGESTIONS, EMPTY_STATE_PROMPTS } from '../data/agent-ui'

function ChatPage() {
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState([])
  const textareaRef = useRef(null)
  const streamRef = useRef(null)

  const hasMessages = messages.length > 0

  useLayoutEffect(() => {
    const textarea = textareaRef.current
    if (!textarea) {
      return
    }

    textarea.style.height = '0px'
    const nextHeight = Math.min(textarea.scrollHeight, 220)
    textarea.style.height = `${nextHeight}px`
  }, [input, hasMessages])

  useEffect(() => {
    if (!hasMessages || !streamRef.current) {
      return
    }

    streamRef.current.scrollTop = streamRef.current.scrollHeight
  }, [messages, hasMessages])

  const submitMessage = (messageText) => {
    const trimmed = messageText.trim()
    if (!trimmed) {
      return
    }

    setMessages((current) => [
      ...current,
      { id: `u-${current.length + 1}`, role: 'user', message: trimmed },
      {
        id: `a-${current.length + 2}`,
        role: 'agent',
        message:
          '这里先保留聊天页结构。后续接入 /api/agent/chat 后，这里会显示真实的 Agent 回复、推荐歌单摘要和播放状态。',
      },
    ])
    setInput('')
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
        {!hasMessages ? (
          <section className={styles.EmptyState}>
            <div className={styles.EmptyContent}>
              <p className={styles.Kicker}>AgentMusic</p>
              <h1>你想听什么？</h1>
              <p className={styles.Subtitle}>
                通过自然语言生成推荐歌单、调整播放、查看历史版本，然后再切换到音乐主界面。
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
              />
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
              {messages.map((item) => (
                <article
                  key={item.id}
                  className={`${styles.MessageBubble} ${
                    item.role === 'agent' ? styles.AgentBubble : styles.UserBubble
                  }`}
                >
                  <p>{item.message}</p>
                </article>
              ))}
            </div>

            <div className={styles.BottomComposer}>
              <Composer
                input={input}
                textareaRef={textareaRef}
                onInputChange={setInput}
                onKeyDown={handleKeyDown}
                onSubmit={submitMessage}
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
        />
        <div className={styles.InputActions}>
          <TooltipIconButton tooltip="点击进行语音输入或长按 Ctrl+M">
            <Sound />
          </TooltipIconButton>
          <TooltipIconButton tooltip="发送" filled onClick={() => onSubmit(input)}>
            <span className={styles.SendArrow}>↑</span>
          </TooltipIconButton>
        </div>
      </div>
    </div>
  )
}

function TooltipIconButton({ children, tooltip, onClick, filled = false }) {
  return (
    <button
      className={`${styles.IconButton} ${filled ? styles.FilledIconButton : ''}`}
      type="button"
      onClick={onClick}
    >
      {children}
      <span className={styles.Tooltip}>{tooltip}</span>
    </button>
  )
}

export default ChatPage
