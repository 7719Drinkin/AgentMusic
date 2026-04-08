import { connect } from 'react-redux'
import styles from './current-playing-panel.module.css'

const VIEW_LABELS = {
  details: '当前播放',
  lyrics: '歌词',
  queue: '队列',
}

function CurrentPlayingPanel({ trackData, currentPlaylistId, currentTrackIndex, isPlaying, view, onClose, onSwitchView }) {
  const coverSrc = trackData.trackImg || '/image/Playlist/liked-songs.PNG'

  return (
    <aside className={styles.panel}>
      <div className={styles.panelHeader}>
        <div>
          <p className={styles.overline}>当前播放</p>
          <h2>{VIEW_LABELS[view] || '当前播放'}</h2>
        </div>
        <button className={styles.closeButton} type="button" onClick={onClose} aria-label="关闭当前播放栏">
          ×
        </button>
      </div>

      <div className={styles.coverCard}>
        <img className={styles.coverArt} src={coverSrc} alt={trackData.trackName || '当前曲目封面'} />
        <div className={styles.meta}>
          <h3>{trackData.trackName || '暂未播放'}</h3>
          <p>{trackData.trackArtist || '等待推荐结果'}</p>
          <div className={styles.metaPills}>
            <span>{isPlaying ? '正在播放' : '已暂停'}</span>
            <span>{trackData.trackId ? '会话驱动' : '本地测试音源'}</span>
          </div>
        </div>
      </div>

      <div className={styles.quickTabs}>
        <button type="button" className={`${styles.tabButton} ${view === 'details' ? styles.activeTab : ''}`} onClick={() => onSwitchView('details')}>
          当前播放
        </button>
        <button type="button" className={`${styles.tabButton} ${view === 'lyrics' ? styles.activeTab : ''}`} onClick={() => onSwitchView('lyrics')}>
          歌词
        </button>
        <button type="button" className={`${styles.tabButton} ${view === 'queue' ? styles.activeTab : ''}`} onClick={() => onSwitchView('queue')}>
          队列
        </button>
      </div>

      <PanelBody view={view} trackData={trackData} currentPlaylistId={currentPlaylistId} currentTrackIndex={currentTrackIndex} />
    </aside>
  )
}

function PanelBody({ view, trackData, currentPlaylistId, currentTrackIndex }) {
  if (view === 'lyrics') {
    return (
      <section className={styles.section}>
        <h4>歌词</h4>
        <p>歌词功能保留在 Priority 2。当前先提供面板入口和布局占位，后续接真实歌词数据。</p>
      </section>
    )
  }

  if (view === 'queue') {
    return (
      <section className={styles.section}>
        <h4>播放队列</h4>
        <ul className={styles.queueList}>
          <li>
            <span>当前歌曲</span>
            <strong>{trackData.trackName || '等待推荐结果'}</strong>
          </li>
          <li>
            <span>歌单上下文</span>
            <strong>{currentPlaylistId || '尚未绑定推荐歌单'}</strong>
          </li>
          <li>
            <span>当前索引</span>
            <strong>{typeof currentTrackIndex === 'number' ? currentTrackIndex + 1 : '未设置'}</strong>
          </li>
        </ul>
      </section>
    )
  }

  return (
    <>
      <section className={styles.section}>
        <h4>关于这首歌</h4>
        <p>当前播放栏先承担播放器详情面板的职责。后续可以在这里叠加 Agent 推荐理由、当前歌单来源和更多歌曲信息。</p>
      </section>
      <section className={styles.section}>
        <h4>当前状态</h4>
        <ul className={styles.statusList}>
          <li>
            <span>曲目 ID</span>
            <strong>{trackData.trackId || '本地测试曲目'}</strong>
          </li>
          <li>
            <span>推荐歌单</span>
            <strong>{currentPlaylistId || '尚未关联'}</strong>
          </li>
          <li>
            <span>歌单索引</span>
            <strong>{typeof currentTrackIndex === 'number' ? currentTrackIndex + 1 : '未设置'}</strong>
          </li>
        </ul>
      </section>
    </>
  )
}

const mapStateToProps = (state) => {
  return {
    trackData: state.trackData,
    isPlaying: state.isPlaying,
    currentPlaylistId: state.currentPlaylistId,
    currentTrackIndex: state.currentTrackIndex,
  }
}

export default connect(mapStateToProps)(CurrentPlayingPanel)
