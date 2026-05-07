import { useEffect, useMemo, useState } from 'react'
import { connect } from 'react-redux'
import { useHistory } from 'react-router-dom'
import { fetchArtist } from '../../api/music'
import { fetchPlaylistDetail } from '../../api/playlists'
import * as Icons from '../icons'
import CurrentPlayingMenu from './current-playing-menu'
import CurrentPlayingQueueDrawer from './current-playing-queue-drawer'
import {
  QUEUE_PLAY_REQUEST_EVENT,
  QUEUE_NEXT_REQUEST_EVENT,
  buildArtistSearchLocation,
  buildCredits,
  mapQueueItems,
  resolveNextQueueItem,
  resolveCurrentTrackFromPlaylist,
} from './current-playing-helpers'
import styles from './current-playing-panel.module.css'

function CurrentPlayingPanel({ trackData, currentPlaylistId, currentTrackIndex, isQueueOpen, onClose, onToggleQueue }) {
  const history = useHistory()
  const [menuOpen, setMenuOpen] = useState(false)
  const [creditsExpanded, setCreditsExpanded] = useState(false)
  const [playlistDetail, setPlaylistDetail] = useState(null)
  const [artistDirectory, setArtistDirectory] = useState({})

  useEffect(() => {
    let cancelled = false

    async function loadPanelData() {
      if (!currentPlaylistId) {
        if (!cancelled) {
          setPlaylistDetail(null)
          setArtistDirectory({})
        }
        return
      }

      try {
        const detail = await fetchPlaylistDetail(currentPlaylistId)
        if (cancelled) {
          return
        }
        setPlaylistDetail(detail)

        const artistIds = [...new Set(
          (detail?.tracks ?? [])
            .map((item) => item?.track?.artistId)
            .filter(Boolean),
        )]

        if (artistIds.length === 0) {
          setArtistDirectory({})
          return
        }

        const artistEntries = await Promise.all(
          artistIds.map(async (artistId) => {
            try {
              const artist = await fetchArtist(artistId)
              return [artistId, artist]
            } catch {
              return [artistId, null]
            }
          }),
        )

        if (!cancelled) {
          setArtistDirectory(Object.fromEntries(artistEntries))
        }
      } catch {
        if (!cancelled) {
          setPlaylistDetail(null)
          setArtistDirectory({})
        }
      }
    }

    loadPanelData()

    return () => {
      cancelled = true
    }
  }, [currentPlaylistId])

  const currentTrack = useMemo(
    () => resolveCurrentTrackFromPlaylist(playlistDetail, currentTrackIndex, trackData),
    [playlistDetail, currentTrackIndex, trackData],
  )

  const playlistTitle = playlistDetail?.name || 'Now playing'
  const currentArtist = currentTrack?.artistId ? artistDirectory[currentTrack.artistId] : null
  const displayArtistName = currentArtist?.name || trackData.trackArtist || 'Waiting for recommendation'
  const coverSrc = currentTrack?.albumImageUrl || trackData.trackImg || '/image/Playlist/liked-songs.PNG'
  const artistSummary = currentArtist?.bio
    || (currentArtist?.followers
      ? `${displayArtistName} has about ${formatFollowers(currentArtist.followers)} followers on Spotify.`
      : (displayArtistName
        ? `${displayArtistName} is the primary artist of the current track. More biography and catalog details will appear here after richer metadata is connected.`
        : 'Artist profile details will appear here after richer metadata is connected.'))

  const queueItems = useMemo(
    () => mapQueueItems(playlistDetail, currentTrackIndex).map((item) => ({
      ...item,
      songArtist: artistDirectory[item.songArtistId]?.name || displayArtistName,
    })),
    [playlistDetail, currentTrackIndex, artistDirectory, displayArtistName],
  )
  const nextQueueItem = useMemo(
    () => resolveNextQueueItem(queueItems, currentTrackIndex),
    [queueItems, currentTrackIndex],
  )
  const credits = useMemo(() => buildCredits(currentTrack, displayArtistName), [currentTrack, displayArtistName])
  const currentTrackBadge = currentTrack?.albumName ? `From ${currentTrack.albumName}` : 'Live playback context'
  const queueSize = playlistDetail?.tracks?.length ?? 0
  const currentOrdinal = queueSize > 0
    ? Math.min(Math.max((currentTrackIndex ?? 0) + 1, 1), queueSize)
    : 0
  const remainingQueueCount = queueSize > 0 && currentOrdinal > 0
    ? Math.max(queueSize - currentOrdinal, 0)
    : 0

  const handleArtistNavigation = () => {
    if (!displayArtistName) {
      return
    }

    history.push(
      buildArtistSearchLocation(displayArtistName, {
        artistImage: currentArtist?.imageUrl || coverSrc,
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

  const handleQueueTrackSelect = (queueItem) => {
    if (!queueItem || queueItem.isCurrent) {
      return
    }

    window.dispatchEvent(
      new CustomEvent(QUEUE_PLAY_REQUEST_EVENT, {
        detail: {
          trackId: queueItem.trackId,
          trackIndex: queueItem.trackIndex,
        },
      }),
    )
    onToggleQueue()
  }

  return (
    <div className={styles.panelShell}>
      <aside className={styles.panel}>
        <header className={styles.header}>
          <button className={styles.headerAction} type="button" aria-label="Hide now playing view" onClick={onClose}>
            <Icons.Prevpage />
          </button>
          <div className={styles.headerTitleWrap}>
            <p className={styles.headerLabel}>Now playing</p>
            <h2 className={styles.headerTitle}>{playlistTitle}</h2>
          </div>
          <div className={styles.headerActionGroup}>
            <button
              className={styles.headerAction}
              type="button"
              aria-label={`More actions for ${currentTrack?.title || 'current track'}`}
              onClick={() => setMenuOpen((current) => !current)}
            >
              <Icons.More />
            </button>
            {menuOpen ? (
              <CurrentPlayingMenu
                trackName={currentTrack?.title || trackData.trackName}
                onClose={() => setMenuOpen(false)}
                onGoArtist={handleArtistNavigation}
              />
            ) : null}
          </div>
        </header>

        <div className={styles.content}>
          <section className={styles.contextStrip}>
            <span className={styles.contextPill}>
              {queueSize > 0 ? `Track ${currentOrdinal} of ${queueSize}` : 'Playlist context pending'}
            </span>
            <span className={styles.contextDetail}>
              {queueSize > 0
                ? `${remainingQueueCount} track${remainingQueueCount === 1 ? '' : 's'} queued after this`
                : 'Queue details will appear after recommendation playback starts.'}
            </span>
          </section>

          <div className={styles.coverSection}>
            <img className={styles.coverArt} src={coverSrc} alt={currentTrack?.title || 'Current track cover'} />
          </div>

          <section className={styles.trackMeta}>
            <span className={styles.trackBadge}>{currentTrackBadge}</span>
            <h3 className={styles.trackName}>{currentTrack?.title || trackData.trackName || 'Nothing playing yet'}</h3>
            <button className={styles.artistLink} type="button" onClick={handleArtistNavigation}>
              {displayArtistName}
            </button>
          </section>

          <button className={styles.artistCard} type="button" onClick={handleArtistNavigation}>
            <div className={styles.artistCardHeader}>
              <h4>About the artist</h4>
              <span>Open artist spotlight</span>
            </div>
            <div className={styles.artistCardBody}>
              <img className={styles.artistAvatar} src={currentArtist?.imageUrl || coverSrc} alt={displayArtistName || 'Artist avatar'} />
              <div className={styles.artistSummary}>
                <strong>{displayArtistName || 'Artist details pending'}</strong>
                <p>{artistSummary}</p>
              </div>
            </div>
          </button>

          <section className={styles.card}>
            <button className={styles.cardHeaderButton} type="button" onClick={() => setCreditsExpanded((current) => !current)}>
              <h4>Track credits</h4>
              <span>{creditsExpanded ? 'Collapse' : 'Expand'}</span>
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
              <h4>Up next</h4>
              <button className={styles.inlineTextButton} type="button" onClick={onToggleQueue}>
                Open queue
              </button>
            </div>
            <p className={styles.cardSubline}>
              {remainingQueueCount > 0
                ? `${remainingQueueCount} more track${remainingQueueCount === 1 ? '' : 's'} follow in the current playlist context.`
                : 'No additional queued tracks are available after the current selection.'}
            </p>

            {nextQueueItem ? (
              <button className={styles.nextTrackButton} type="button" onClick={handlePlayNext}>
                <img src={nextQueueItem.songimg} alt={nextQueueItem.songName} />
                <div className={styles.nextTrackMeta}>
                  <strong>{nextQueueItem.songName}</strong>
                  <span>{nextQueueItem.songArtist}</span>
                </div>
              </button>
            ) : (
              <div className={styles.emptyState}>No next track is available in the current playlist context.</div>
            )}
          </section>
        </div>
      </aside>

      <CurrentPlayingQueueDrawer
        isOpen={isQueueOpen}
        queueItems={queueItems}
        playlistTitle={playlistTitle}
        onClose={onToggleQueue}
        onSelectQueueItem={handleQueueTrackSelect}
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

function formatFollowers(count) {
  return new Intl.NumberFormat('en-US').format(count)
}
