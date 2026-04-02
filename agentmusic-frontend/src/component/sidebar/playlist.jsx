import { useEffect, useState } from 'react'
import styles from './playlist.module.css'
import TitleS from '../text/title-s'
import TextRegularM from '../text/text-regular-m'
import PlaylistButton from './playlist-button'
import { PLAYLISTBTN } from '../../constants'
import { fetchRecentPlaylists } from '../../api/playlists'
import { playTrack } from '../../api/playback'

const DEMO_USER_ID = 'demo-user'
const PLAYLIST_REFRESH_EVENT = 'agentmusic:playlists-updated'
const PLAYBACK_REFRESH_EVENT = 'agentmusic:playback-session-updated'

function Playlist() {
  const [playlists, setPlaylists] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')
  const [activePlaylistId, setActivePlaylistId] = useState(null)

  useEffect(() => {
    let cancelled = false

    const loadPlaylists = async () => {
      try {
        const data = await fetchRecentPlaylists(DEMO_USER_ID, 10)
        if (cancelled) {
          return
        }

        setPlaylists(data)
        setErrorMessage('')
      } catch (error) {
        if (cancelled) {
          return
        }

        setErrorMessage(error.message || '推荐歌单加载失败。')
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    loadPlaylists()
    window.addEventListener(PLAYLIST_REFRESH_EVENT, loadPlaylists)

    return () => {
      cancelled = true
      window.removeEventListener(PLAYLIST_REFRESH_EVENT, loadPlaylists)
    }
  }, [])

  const handlePlaylistSelect = async (playlist) => {
    if (!playlist?.tracks?.length) {
      return
    }

    const firstTrack = playlist.tracks[0]?.track
    if (!firstTrack?.trackId) {
      return
    }

    try {
      setActivePlaylistId(playlist.id)
      await playTrack(DEMO_USER_ID, {
        trackId: firstTrack.trackId,
        playlistId: playlist.id,
        trackIndex: 0,
        deviceId: null,
        playbackMode: 'SEQUENTIAL',
      })
      window.dispatchEvent(new CustomEvent(PLAYBACK_REFRESH_EVENT))
      setErrorMessage('')
    } catch (error) {
      setErrorMessage(error.message || '推荐歌单播放失败。')
    }
  }

  return (
    <div className={styles.Playlist}>
      <TitleS>推荐歌单</TitleS>

      <div>
        {PLAYLISTBTN.map((playlist) => (
          <PlaylistButton
            href={playlist.path}
            ImgName={playlist.ImgName}
            key={playlist.title}
          >
            {playlist.title}
          </PlaylistButton>
        ))}
      </div>

      <hr className={styles.hr} />

      <div className={styles.PlaylistList}>
        {isLoading ? <TextRegularM>正在加载推荐歌单...</TextRegularM> : null}
        {!isLoading && !errorMessage && playlists.length === 0 ? (
          <TextRegularM>还没有生成过推荐歌单。</TextRegularM>
        ) : null}
        {errorMessage ? <TextRegularM>{errorMessage}</TextRegularM> : null}
        {playlists.map((playlist) => (
          <button
            key={playlist.id}
            className={`${styles.PlaylistItem} ${activePlaylistId === playlist.id ? styles.ActivePlaylistItem : ''}`}
            type="button"
            onClick={() => handlePlaylistSelect(playlist)}
          >
            <TextRegularM>{playlist.name}</TextRegularM>
            <TextRegularM>
              <small>{playlist.tracks.length} 首</small>
            </TextRegularM>
          </button>
        ))}
      </div>
    </div>
  )
}

export default Playlist
