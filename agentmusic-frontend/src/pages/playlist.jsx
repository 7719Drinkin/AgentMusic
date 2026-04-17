import { useEffect, useMemo, useState } from 'react'
import { connect } from 'react-redux'
import { useParams } from 'react-router'
import { fetchArtist } from '../api/music'
import { fetchPlaylistDetail } from '../api/playlists'
import { playTrack } from '../api/playback'
import Topnav from '../component/topnav/topnav'
import TextRegularM from '../component/text/text-regular-m'
import TitleL from '../component/text/title-l'
import convertTime from '../functions/convertTime'
import * as Icons from '../component/icons'
import styles from './playlist.module.css'

const DEMO_USER_ID = 'demo-user'
const PLAYBACK_REFRESH_EVENT = 'agentmusic:playback-session-updated'

function PlaylistPage(props) {
  const { path } = useParams()
  const playlistId = decodeURIComponent(path)
  const [playlist, setPlaylist] = useState(null)
  const [artistDirectory, setArtistDirectory] = useState({})
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const [isPlaybackBusy, setIsPlaybackBusy] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function loadPlaylist() {
      setIsLoading(true)
      try {
        const detail = await fetchPlaylistDetail(playlistId)
        if (cancelled) {
          return
        }

        setPlaylist(detail)
        setErrorMessage('')

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
              return [artistId, artist?.name || '未知艺人']
            } catch {
              return [artistId, '未知艺人']
            }
          }),
        )

        if (!cancelled) {
          setArtistDirectory(Object.fromEntries(artistEntries))
        }
      } catch (error) {
        if (!cancelled) {
          setPlaylist(null)
          setArtistDirectory({})
          setErrorMessage(error.message || '歌单详情加载失败。')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    loadPlaylist()

    return () => {
      cancelled = true
    }
  }, [playlistId])

  const playlistTracks = playlist?.tracks ?? []
  const playlistCover = playlistTracks[0]?.track?.albumImageUrl || '/image/Playlist/liked-songs.PNG'
  const activePlaylist = props.currentPlaylistId === playlistId

  const playlistRows = useMemo(() => {
    return playlistTracks.map((item, index) => {
      const track = item.track
      return {
        index,
        trackId: track.trackId,
        title: track.title,
        artistName: artistDirectory[track.artistId] || '未知艺人',
        durationLabel: convertTime((track.durationMs || 0) / 1000),
        albumImageUrl: track.albumImageUrl || playlistCover,
        isCurrent: activePlaylist && props.currentTrackIndex === index,
      }
    })
  }, [playlistTracks, artistDirectory, playlistCover, activePlaylist, props.currentTrackIndex])

  const handlePlayTrack = async (trackId, trackIndex) => {
    if (!trackId || isPlaybackBusy) {
      return
    }

    try {
      setIsPlaybackBusy(true)
      await playTrack(DEMO_USER_ID, {
        trackId,
        playlistId,
        trackIndex,
        deviceId: props.deviceId,
        playbackMode: props.playbackMode,
      })
      window.dispatchEvent(new CustomEvent(PLAYBACK_REFRESH_EVENT))
    } catch {
    } finally {
      setIsPlaybackBusy(false)
    }
  }

  const handlePrimaryPlay = async () => {
    const firstTrack = playlistRows[0]
    if (!firstTrack) {
      return
    }

    await handlePlayTrack(firstTrack.trackId, firstTrack.index)
  }

  return (
    <div className={styles.PlaylistPage}>
      <div className={styles.gradientBg}></div>
      <div className={styles.gradientBgSoft}></div>
      <div className={styles.Bg}></div>

      <Topnav />

      <section className={styles.Hero}>
        <img className={styles.Cover} src={playlistCover} alt={playlist?.name || '歌单封面'} />

        <div className={styles.HeroMeta}>
          <span className={styles.PlaylistType}>推荐歌单</span>
          <TitleL>{playlist?.name || '歌单详情'}</TitleL>
          <TextRegularM>
            {isLoading
              ? '正在加载歌单详情...'
              : errorMessage || `${playlistRows.length} 首 · 版本 ${playlist?.version ?? '-'} · ${playlist?.createdAt ?? ''}`}
          </TextRegularM>
        </div>
      </section>

      <section className={styles.PlaylistActions}>
        <button
          className={styles.PlayButton}
          type="button"
          onClick={handlePrimaryPlay}
          disabled={!playlistRows.length || isPlaybackBusy}
          aria-label="播放歌单"
        >
          <Icons.Play />
        </button>
      </section>

      <section className={styles.PlaylistSongs}>
        <div className={styles.ListHead}>
          <p>#</p>
          <p>标题</p>
          <Icons.Time />
        </div>

        {playlistRows.length === 0 && !isLoading ? (
          <div className={styles.EmptyState}>
            <TextRegularM>{errorMessage || '这个歌单里还没有歌曲。'}</TextRegularM>
          </div>
        ) : null}

        {playlistRows.map((row) => (
          <button
            key={row.trackId}
            className={`${styles.SongBtn} ${row.isCurrent ? styles.ActiveSongBtn : ''}`}
            type="button"
            onClick={() => handlePlayTrack(row.trackId, row.index)}
            disabled={isPlaybackBusy}
          >
            <span className={styles.RowIndex}>{row.index + 1}</span>
            <span className={styles.RowTrack}>
              <img src={row.albumImageUrl} alt={row.title} />
              <span className={styles.RowTrackMeta}>
                <strong>{row.title}</strong>
                <TextRegularM>{row.artistName}</TextRegularM>
              </span>
            </span>
            <TextRegularM>{row.durationLabel}</TextRegularM>
          </button>
        ))}
      </section>
    </div>
  )
}

const mapStateToProps = (state) => {
  return {
    playbackMode: state.playbackMode,
    deviceId: state.deviceId,
    currentPlaylistId: state.currentPlaylistId,
    currentTrackIndex: state.currentTrackIndex,
  }
}

export default connect(mapStateToProps)(PlaylistPage)
