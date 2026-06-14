import { useEffect, useMemo, useState } from 'react'
import { connect } from 'react-redux'
import { useParams } from 'react-router'
import { getErrorMessage } from '../api/http'
import { fetchArtist } from '../api/music'
import { fetchPlaybackDevices, playTrack } from '../api/playback'
import { fetchPlaylistDetail } from '../api/playlists'
import * as Icons from '../component/icons'
import TextRegularM from '../component/text/text-regular-m'
import TitleL from '../component/text/title-l'
import Topnav from '../component/topnav/topnav'
import convertTime from '../functions/convertTime'
import styles from './playlist.module.css'

const DEMO_USER_ID = 'demo-user'
const PLAYBACK_REFRESH_EVENT = 'agentmusic:playback-session-updated'

function formatDateTime(value) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function PlaylistPage({
  playbackMode,
  deviceId,
  currentPlaylistId,
  currentTrackIndex,
  isPlaying,
}) {
  const { path } = useParams()
  const playlistId = decodeURIComponent(path)
  const [playlist, setPlaylist] = useState(null)
  const [artistDirectory, setArtistDirectory] = useState({})
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const [isPlaybackBusy, setIsPlaybackBusy] = useState(false)
  const [playbackError, setPlaybackError] = useState('')

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
          setErrorMessage(getErrorMessage(error, '歌单详情加载失败。'))
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
  const activePlaylist = currentPlaylistId === playlistId

  const playlistRows = useMemo(() => {
    return playlistTracks.map((item, index) => {
      const track = item.track
      return {
        index,
        trackId: track.trackId,
        title: track.title,
        artistName: artistDirectory[track.artistId] || '未知艺人',
        albumName: track.albumName || '未知专辑',
        addedAtLabel: formatDateTime(item.addedAt),
        durationLabel: convertTime((track.durationMs || 0) / 1000),
        albumImageUrl: track.albumImageUrl || playlistCover,
        isCurrent: activePlaylist && currentTrackIndex === index,
      }
    })
  }, [activePlaylist, artistDirectory, currentTrackIndex, playlistCover, playlistTracks])

  const createdAtLabel = playlist?.createdAt ? formatDateTime(playlist.createdAt) : '--'
  const playlistMetaText = `${playlistRows.length} 首 · 版本 ${playlist?.version ?? '-'} · 创建于 ${createdAtLabel}`
  const heroStatusLabel = activePlaylist
    ? isPlaying
      ? '当前正在播放'
      : '当前播放上下文'
    : '推荐歌单'
  const playActionTitle = activePlaylist
    ? isPlaying
      ? '继续当前歌单'
      : '恢复这张歌单'
    : '播放这张歌单'
  const playActionDescription = activePlaylist
    ? '从第 1 首开始，沿用当前播放模式与设备上下文。当前歌单会保持与播放器状态同步。'
    : '从第 1 首开始接管当前播放会话，并沿用当前设备与播放模式。'

  const loadAvailableDevices = async () => {
    const devices = await fetchPlaybackDevices(DEMO_USER_ID)
    return Array.isArray(devices) ? devices : []
  }

  const handlePlayTrack = async (trackId, trackIndex) => {
    if (!trackId || isPlaybackBusy) {
      return
    }

    try {
      setIsPlaybackBusy(true)
      setPlaybackError('')

      const devices = await loadAvailableDevices()
      if (devices.length === 0) {
        setPlaybackError('当前没有可用的 Spotify 设备。请启用 AgentMusic Web Player 后重试。')
        return
      }

      await playTrack(DEMO_USER_ID, {
        trackId,
        playlistId,
        trackIndex,
        deviceId,
        playbackMode,
      })
      window.dispatchEvent(new CustomEvent(PLAYBACK_REFRESH_EVENT))
    } catch (error) {
      setPlaybackError(getErrorMessage(error, '播放这首歌失败。'))
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
          <div className={styles.HeroEyebrowRow}>
            <span className={styles.PlaylistType}>推荐歌单</span>
            <span className={styles.HeroStatePill}>{heroStatusLabel}</span>
          </div>
          <TitleL>{playlist?.name || '歌单详情'}</TitleL>
          <div className={styles.PlaylistMetaRow}>
            <span className={styles.MetaChip}>{playlistRows.length} 首</span>
            <span className={styles.MetaChip}>版本 {playlist?.version ?? '-'}</span>
            <span className={styles.MetaChip}>创建于 {createdAtLabel}</span>
          </div>
          <TextRegularM className={styles.PlaylistMetaLead}>
            {isLoading
              ? '正在加载歌单详情...'
              : errorMessage || `${playlistMetaText} · 根据当前对话上下文生成。`}
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
        <div className={styles.PlayActionText}>
          <strong>{isPlaybackBusy ? '正在切换播放...' : playActionTitle}</strong>
          <span>{playActionDescription}</span>
        </div>
      </section>

      {playbackError ? (
        <section className={styles.PlaybackStatus}>
          <TextRegularM>{playbackError}</TextRegularM>
        </section>
      ) : null}

      <section className={styles.PlaylistSongs}>
        <div className={styles.ListHead}>
          <p>#</p>
          <p>标题</p>
          <p>专辑</p>
          <p>添加时间</p>
          <Icons.Time />
        </div>

        {playlistRows.length === 0 && !isLoading ? (
          <div className={styles.EmptyState}>
            <TextRegularM>{errorMessage || '这个歌单里还没有歌曲。'}</TextRegularM>
          </div>
        ) : null}

        {playlistRows.map((row) => {
          const rowStateLabel = row.isCurrent
            ? isPlaying
              ? '当前播放'
              : '当前曲目'
            : ''

          return (
            <button
              key={row.trackId}
              className={`${styles.SongBtn} ${row.isCurrent ? styles.ActiveSongBtn : ''}`.trim()}
              type="button"
              onClick={() => handlePlayTrack(row.trackId, row.index)}
              disabled={isPlaybackBusy}
              data-testid="playlist-song-row"
            >
              <span className={styles.RowIndex}>{row.index + 1}</span>
              <span className={styles.RowTrack}>
                <img src={row.albumImageUrl} alt={row.title} />
                <span className={styles.RowTrackMeta}>
                  <span className={styles.RowTitleLine}>
                    <strong>{row.title}</strong>
                    {rowStateLabel ? <span className={styles.RowStatePill}>{rowStateLabel}</span> : null}
                  </span>
                  <TextRegularM>{row.artistName}</TextRegularM>
                </span>
              </span>
              <TextRegularM className={styles.RowAlbum}>{row.albumName}</TextRegularM>
              <TextRegularM className={styles.RowAddedAt}>{row.addedAtLabel}</TextRegularM>
              <TextRegularM className={styles.RowDuration}>{row.durationLabel}</TextRegularM>
            </button>
          )
        })}
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
    isPlaying: state.isPlaying,
  }
}

export default connect(mapStateToProps)(PlaylistPage)
