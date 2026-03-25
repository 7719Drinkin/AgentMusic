import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Topnav from '../component/topnav/topnav'
import styles from './chat.module.css'
import { CHAT_SUGGESTIONS, EMPTY_STATE_PROMPTS } from '../data/agent-ui'

function ChatPage() {
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState([])

  const hasMessages = messages.length > 0

  const pageClassName = useMemo(
    () => `${styles.ChatPage} ${hasMessages ? styles.HasMessages : styles.EmptyPage}`,
    [hasMessages],
  )

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
          '这里先保留聊天页结构。后续接上 /api/agent/chat 后，这里会显示真实的 Agent 回复、推荐歌单摘要和播放状态。',
      },
    ])
    setInput('')
  }

  return (
    <div className={pageClassName}>
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
                onInputChange={setInput}
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
                <p className={styles.Kicker}>Agent Chat</p>
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

            <div className={styles.ChatStream}>
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
                onInputChange={setInput}
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

function Composer({ input, onInputChange, onSubmit, centered = false }) {
  const wrapperClassName = centered
    ? `${styles.Composer} ${styles.CenteredComposer}`
    : styles.Composer

  return (
    <div className={wrapperClassName}>
      <div className={styles.InputShell}>
        <textarea
          className={styles.ChatInput}
          rows={centered ? 3 : 2}
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          placeholder="给 AgentMusic 发送消息"
        />
        <div className={styles.InputActions}>
          <button className={styles.IconButton} type="button" aria-label="语音输入">
            语音
          </button>
          <button className={styles.SendButton} type="button" onClick={() => onSubmit(input)}>
            发送
          </button>
        </div>
      </div>
    </div>
  )
}

export default ChatPage
