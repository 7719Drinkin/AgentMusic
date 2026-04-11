import { useMemo, useState } from 'react'
import { connect } from 'react-redux'
import { useHistory } from 'react-router-dom'
import * as Icons from '../icons'
import CurrentPlayingMenu from './current-playing-menu'
import CurrentPlayingQueueDrawer from './current-playing-queue-drawer'
import {
  QUEUE_NEXT_REQUEST_EVENT,
  buildArtistSearchLocation,
  buildCredits,
  resolveCurrentPlaylistTitle,
  resolveNextQueueItem,
  resolveQueueItems,
} from './current-playing-helpers'
import styles from './current-playing-panel.module.css'

function CurrentPlayingPanel({ trackData, currentPlaylistId, currentTrackIndex, isQueueOpen, onClose, onToggleQueue }) {
  const history = useHistory()
  const [menuOpen, setMenuOpen] = useState(false)
  const [creditsExpanded, setCreditsExpanded] = useState(false)

  const playlistTitle = useMemo(
    () => resolveCurrentPlaylistTitle(trackData, currentPlaylistId, currentTrackIndex),
    [trackData, currentPlaylistId, currentTrackIndex],
  )
  const nextQueueItem = useMemo(
    () => resolveNextQueueItem(trackData, currentPlaylistId, currentTrackIndex),
    [trackData, currentPlaylistId, currentTrackIndex],
  )
  const queueItems = useMemo(
    () => resolveQueueItems(trackData, currentPlaylistId, currentTrackIndex),
    [trackData, currentPlaylistId, currentTrackIndex],
  )
  const credits = useMemo(() => buildCredits(trackData.trackArtist), [trackData.trackArtist])
  const coverSrc = trackData.trackImg || '/image/Playlist/liked-songs.PNG'

  const handleArtistNavigation = () => {
    if (!trackData.trackArtist) {
      return
    }

    history.push(
      buildArtistSearchLocation(trackData.trackArtist, {
        artistImage: coverSrc,
        from: 'current-playing-panel',
      }),
    )
  }

  const handlePlayNext = () => {
    if (!nextQueueItem) {
      return
    }

    window.dispatchEvent(new CustomEvent(QUEUE_NEXT_REQUEST_EVENT))
  }

  return (
    <div className={styles.panelShell}>
      <aside className={styles.panel}>
        <header className={styles.header}>
          <button className={styles.headerAction} type="button" aria-label="隐藏当前播放视图" onClick={onClose}>
            <Icons.Prevpage />
          </button>
          <div className={styles.headerTitleWrap}>
            <p className={styles.headerLabel}>当前播放栏</p>
            <h2 className={styles.headerTitle}>{playlistTitle}</h2>
          </div>
          <div className={styles.headerActionGroup}>
            <button
              className={styles.headerAction}
              type="button"
              aria-label={`更多有关 ${trackData.trackName || '当前歌曲'} 的选项`}
              onClick={() => setMenuOpen((current) => !current)}
            >
              <Icons.More />
            </button>
            {menuOpen ? (
              <CurrentPlayingMenu
                trackName={trackData.trackName}
                onClose={() => setMenuOpen(false)}
                onGoArtist={handleArtistNavigation}
              />
            ) : null}
          </div>
        </header>
        <div className={styles.content}>
          <div className={styles.coverSection}>
            <img className={styles.coverArt} src={coverSrc} alt={trackData.trackName || '当前曲目封面'} />
          </div>

          <section className={styles.trackMeta}>
            <h3 className={styles.trackName}>{trackData.trackName || '暂无播放'}</h3>
            <button className={styles.artistLink} type="button" onClick={handleArtistNavigation}>
              {trackData.trackArtist || '等待推荐'}
            </button>
          </section>

          <button className={styles.artistCard} type="button" onClick={handleArtistNavigation}>
            <div className={styles.artistCardHeader}>
              <h4>关于艺人</h4>
              <span>转至艺人</span>
            </div>
            <div className={styles.artistCardBody}>
              <img className={styles.artistAvatar} src={coverSrc} alt={trackData.trackArtist || '艺人头像'} />
              <div className={styles.artistSummary}>
                <strong>{trackData.trackArtist || '待接入艺人信息'}</strong>
                <p>
                  {trackData.trackArtist
                    ? `${trackData.trackArtist} 是当前播放曲目的主要艺人。接入真实艺人资料后，这里将展示艺人简介与代表作品。`
                    : '接入真实艺人资料后，这里将展示头像、简介与代表作品。'}
                </p>
              </div>
            </div>
          </button>

          <section className={styles.card}>
            <button className={styles.cardHeaderButton} type="button" onClick={() => setCreditsExpanded((current) => !current)}>
              <h4>歌曲提供者</h4>
              <span>{creditsExpanded ? '收起' : '展开'}</span>
            </button>
            <div className={styles.creditList}>
              {credits.slice(0, creditsExpanded ? credits.length : 3).map((credit) => (
                <div className={styles.creditRow} key={`${credit.label}-${credit.value}`}>
                  <span>{credit.label}</span>
                  <strong>{credit.value}</strong>
                </div>
              ))}
            </div>
          </section>

          <section className={styles.card}>
            <div className={styles.cardHeader}>
              <h4>队列中的下一首歌</h4>
              <button className={styles.inlineTextButton} type="button" onClick={onToggleQueue}>
                打开队列
              </button>
            </div>

            {nextQueueItem ? (
              <button className={styles.nextTrackButton} type="button" onClick={handlePlayNext}>
                <img src={nextQueueItem.songimg} alt={nextQueueItem.songName} />
                <div className={styles.nextTrackMeta}>
                  <strong>{nextQueueItem.songName}</strong>
                  <span>{nextQueueItem.songArtist}</span>
                </div>
              </button>
            ) : (
              <div className={styles.emptyState}>当前歌单中没有下一首歌。</div>
            )}
          </section>
        </div>
      </aside>

      <CurrentPlayingQueueDrawer
        isOpen={isQueueOpen}
        queueItems={queueItems}
        playlistTitle={playlistTitle}
        onClose={onToggleQueue}
      />
    </div>
  )
}

const mapStateToProps = (state) => {
  return {
    trackData: state.trackData,
    currentPlaylistId: state.currentPlaylistId,
    currentTrackIndex: state.currentTrackIndex,
  }
}

export default connect(mapStateToProps)(CurrentPlayingPanel)
