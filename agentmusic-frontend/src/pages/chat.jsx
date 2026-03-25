import { Link } from 'react-router-dom'
import Topnav from '../component/topnav/topnav'
import styles from './chat.module.css'
import {
  AGENT_CHAT_HISTORY,
  AGENT_FUNCTION_BLOCKS,
  CHAT_SUGGESTIONS,
  CURRENT_PLAYBACK_CARD,
  PLAYLIST_HISTORY,
  RECOMMENDATION_PREVIEW,
} from '../data/agent-ui'

function ChatPage() {
  return (
    <div className={styles.ChatPage}>
      <div className={styles.HoverBg}></div>
      <div className={styles.Bg}></div>

      <Topnav />

      <div className={styles.Content}>
        <section className={styles.ChatWorkspace}>
          <header className={styles.ChatHeader}>
            <div>
              <p className={styles.SectionKicker}>Agent Workspace</p>
              <h1>通过自然语言生成、管理并播放推荐歌单</h1>
              <p className={styles.SectionDesc}>
                这是 AgentMusic 的主入口。用户先和 LLM 对话，再决定是否切换到音乐浏览页面。
              </p>
            </div>
            <div className={styles.HeaderActions}>
              <Link className={styles.SecondaryBtn} to="/music">
                打开音乐主界面
              </Link>
              <button className={styles.PrimaryBtn} type="button">
                连接 Spotify Bridge
              </button>
            </div>
          </header>

          <div className={styles.ChatBody}>
            <div className={styles.ChatStream}>
              {AGENT_CHAT_HISTORY.map((item) => (
                <article
                  key={item.id}
                  className={`${styles.MessageBubble} ${
                    item.role === 'agent' ? styles.AgentBubble : styles.UserBubble
                  }`}
                >
                  <span className={styles.MessageRole}>
                    {item.role === 'agent' ? 'Agent' : 'You'}
                  </span>
                  <p>{item.message}</p>
                  {item.metadata ? <small>{item.metadata}</small> : null}
                </article>
              ))}
            </div>

            <div className={styles.ComposerPanel}>
              <div className={styles.ComposerLabelRow}>
                <strong>对话输入</strong>
                <button className={styles.VoiceBtn} type="button">
                  语音输入
                </button>
              </div>

              <textarea
                className={styles.ChatInput}
                rows={4}
                placeholder="例如：给我来点适合雨夜写代码的粤语歌，然后直接播放。"
              />

              <div className={styles.SuggestionRow}>
                {CHAT_SUGGESTIONS.map((suggestion) => (
                  <button key={suggestion} className={styles.SuggestionChip} type="button">
                    {suggestion}
                  </button>
                ))}
              </div>

              <div className={styles.ComposerActions}>
                <button className={styles.SecondaryBtn} type="button">
                  查看 API 状态
                </button>
                <button className={styles.PrimaryBtn} type="button">
                  发送给 Agent
                </button>
              </div>
            </div>
          </div>
        </section>

        <aside className={styles.SidePanel}>
          <section className={styles.Card}>
            <div className={styles.CardHeader}>
              <div>
                <p className={styles.SectionKicker}>Recommendation Preview</p>
                <h2>{RECOMMENDATION_PREVIEW.title}</h2>
              </div>
              <span>{RECOMMENDATION_PREVIEW.subtitle}</span>
            </div>
            <p className={styles.CardDesc}>{RECOMMENDATION_PREVIEW.summary}</p>
            <div className={styles.TagRow}>
              {RECOMMENDATION_PREVIEW.tags.map((tag) => (
                <span key={tag} className={styles.Tag}>
                  {tag}
                </span>
              ))}
            </div>
            <div className={styles.TrackList}>
              {RECOMMENDATION_PREVIEW.tracks.map((track) => (
                <div key={`${track.title}-${track.artist}`} className={styles.TrackRow}>
                  <div>
                    <strong>{track.title}</strong>
                    <small>{track.artist}</small>
                  </div>
                  <span>{track.duration}</span>
                </div>
              ))}
            </div>
          </section>

          <section className={styles.Card}>
            <div className={styles.CardHeader}>
              <div>
                <p className={styles.SectionKicker}>Playlist History</p>
                <h2>历史推荐版本</h2>
              </div>
            </div>
            <div className={styles.HistoryList}>
              {PLAYLIST_HISTORY.map((item) => (
                <button key={item.id} className={styles.HistoryItem} type="button">
                  <strong>{item.name}</strong>
                  <span>{item.createdAt}</span>
                  <small>{item.note}</small>
                </button>
              ))}
            </div>
          </section>

          <section className={styles.Card}>
            <div className={styles.CardHeader}>
              <div>
                <p className={styles.SectionKicker}>Now Playing</p>
                <h2>当前播放信息</h2>
              </div>
            </div>
            <div className={styles.PlaybackMeta}>
              <strong>{CURRENT_PLAYBACK_CARD.trackTitle}</strong>
              <span>{CURRENT_PLAYBACK_CARD.artist}</span>
              <small>{CURRENT_PLAYBACK_CARD.album}</small>
            </div>
            <div className={styles.PlaybackGrid}>
              <div>
                <label>设备</label>
                <p>{CURRENT_PLAYBACK_CARD.device}</p>
              </div>
              <div>
                <label>模式</label>
                <p>{CURRENT_PLAYBACK_CARD.mode}</p>
              </div>
              <div>
                <label>进度</label>
                <p>{CURRENT_PLAYBACK_CARD.progress}</p>
              </div>
              <div>
                <label>歌词预览</label>
                <p>{CURRENT_PLAYBACK_CARD.lyricsPreview}</p>
              </div>
            </div>
          </section>

          <section className={styles.Card}>
            <div className={styles.CardHeader}>
              <div>
                <p className={styles.SectionKicker}>Feature Coverage</p>
                <h2>前端已补齐的关键入口</h2>
              </div>
            </div>
            <div className={styles.FeatureGrid}>
              {AGENT_FUNCTION_BLOCKS.map((item) => (
                <div key={item.title} className={styles.FeatureItem}>
                  <strong>{item.title}</strong>
                  <p>{item.description}</p>
                </div>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </div>
  )
}

export default ChatPage
