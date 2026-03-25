import './app.css'
import {
  chatMessages,
  navItems,
  playlistCards,
  queueTracks,
  type ChatMessage,
  type NavItem,
  type PlaylistCard,
  type QueueTrack,
} from './data/mock-data'

function App() {
  return (
    <div className="app-shell">
      <Sidebar items={navItems} />
      <main className="workspace">
        <TopBar />
        <section className="main-grid">
          <ChatPanel messages={chatMessages} />
          <AsidePanel playlists={playlistCards} queue={queueTracks} />
        </section>
        <PlayerBar />
      </main>
    </div>
  )
}

function Sidebar({ items }: { items: NavItem[] }) {
  return (
    <aside className="sidebar">
      <div className="brand-block">
        <p className="brand-kicker">AgentMusic</p>
        <h1 className="brand-title">Chat-first music control</h1>
        <p className="brand-copy">
          新前端以对话为主入口，保留播放器与歌单能力作为结构骨架。
        </p>
      </div>

      <nav className="nav-list" aria-label="Primary navigation">
        {items.map((item) => (
          <button
            key={item.id}
            className={`nav-item ${item.active ? 'is-active' : ''}`}
            type="button"
          >
            <span>{item.label}</span>
            <small>{item.hint}</small>
          </button>
        ))}
      </nav>

      <section className="sidebar-card">
        <p className="sidebar-label">Reference Migration</p>
        <strong>Round 1</strong>
        <p>
          先迁移侧栏、顶部导航和底部播放器结构，不迁移旧 Redux 和旧路由。
        </p>
      </section>
    </aside>
  )
}

function TopBar() {
  return (
    <header className="topbar">
      <div>
        <p className="eyebrow">Primary Scene</p>
        <h2>和 LLM 对话生成并控制推荐歌单</h2>
      </div>
      <div className="topbar-actions">
        <button type="button" className="ghost-btn">
          Connect Spotify
        </button>
        <button type="button" className="solid-btn">
          Start Bridge Session
        </button>
      </div>
    </header>
  )
}

function ChatPanel({ messages }: { messages: ChatMessage[] }) {
  return (
    <section className="chat-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Agent Chat</p>
          <h3>主界面改为对话主导</h3>
        </div>
        <span className="status-pill">Planner online</span>
      </div>

      <div className="chat-stream">
        {messages.map((message) => (
          <article
            key={message.id}
            className={`chat-bubble ${message.role === 'agent' ? 'agent' : 'user'}`}
          >
            <p className="chat-role">{message.role === 'agent' ? 'Agent' : 'You'}</p>
            <p className="chat-content">{message.content}</p>
            {message.meta ? <small className="chat-meta">{message.meta}</small> : null}
          </article>
        ))}
      </div>

      <div className="composer">
        <div className="composer-hints">
          <span>支持自然语言推荐</span>
          <span>支持播放控制</span>
          <span>支持歌单历史</span>
        </div>
        <div className="composer-row">
          <textarea
            className="composer-input"
            rows={3}
            placeholder="例如：给我来点适合雨夜写代码的粤语歌，然后直接播放。"
          />
          <button type="button" className="send-btn">
            Send
          </button>
        </div>
      </div>
    </section>
  )
}

function AsidePanel({
  playlists,
  queue,
}: {
  playlists: PlaylistCard[]
  queue: QueueTrack[]
}) {
  return (
    <aside className="aside-panel">
      <section className="card-block">
        <div className="section-heading compact">
          <div>
            <p className="eyebrow">Recommendation History</p>
            <h3>推荐歌单版本</h3>
          </div>
        </div>
        <div className="playlist-stack">
          {playlists.map((playlist) => (
            <article key={playlist.id} className="playlist-card">
              <div className="playlist-meta">
                <strong>{playlist.name}</strong>
                <span>{playlist.trackCount} tracks</span>
              </div>
              <p className="playlist-tag">{playlist.mood}</p>
              <p className="playlist-summary">{playlist.summary}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="card-block">
        <div className="section-heading compact">
          <div>
            <p className="eyebrow">Playback Queue</p>
            <h3>当前播放视图</h3>
          </div>
        </div>
        <div className="queue-list">
          {queue.map((track, index) => (
            <div key={track.id} className="queue-row">
              <span className="queue-index">{String(index + 1).padStart(2, '0')}</span>
              <div className="queue-text">
                <strong>{track.title}</strong>
                <small>{track.artist}</small>
              </div>
              <span className="queue-length">{track.length}</span>
            </div>
          ))}
        </div>
      </section>
    </aside>
  )
}

function PlayerBar() {
  return (
    <footer className="player-bar">
      <div className="player-now">
        <div className="cover-art" />
        <div>
          <strong>K歌之王</strong>
          <small>陈奕迅 · Late Night Report Session</small>
        </div>
      </div>
      <div className="player-center">
        <div className="player-controls">
          <button type="button">Shuffle</button>
          <button type="button">Prev</button>
          <button type="button" className="play-main">
            Play
          </button>
          <button type="button">Next</button>
          <button type="button">Loop</button>
        </div>
        <div className="progress-row">
          <span>01:12</span>
          <div className="progress-track">
            <div className="progress-value" />
          </div>
          <span>03:42</span>
        </div>
      </div>
      <div className="player-side">
        <small>Bridge Device</small>
        <strong>Desktop Spotify</strong>
      </div>
    </footer>
  )
}

export default App
