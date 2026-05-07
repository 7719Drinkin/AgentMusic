import { useEffect, useMemo, useState } from 'react'
import { useHistory, useLocation } from 'react-router-dom'
import { getErrorMessage } from '../../api/http'
import { fetchRecentPlaylists } from '../../api/playlists'
import TextRegularM from '../text/text-regular-m'
import TitleS from '../text/title-s'
import styles from './playlist.module.css'

const DEMO_USER_ID = 'demo-user'
const PLAYLIST_REFRESH_EVENT = 'agentmusic:playlists-updated'

function Playlist() {
  const history = useHistory()
  const location = useLocation()
  const [playlists, setPlaylists] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

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

        setErrorMessage(getErrorMessage(error, 'Failed to load recommended playlists.'))
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

  const activePlaylistId = useMemo(() => {
    const match = location.pathname.match(/^\/playlist\/(.+)$/)
    return match ? decodeURIComponent(match[1]) : null
  }, [location.pathname])

  const handlePlaylistSelect = (playlist) => {
    if (!playlist?.id) {
      return
    }

    history.push(`/playlist/${encodeURIComponent(playlist.id)}`)
  }

  return (
    <div className={styles.Playlist}>
      <div className={styles.PlaylistHeader}>
        <TitleS>Recommended playlists</TitleS>
        <TextRegularM className={styles.PlaylistIntro}>
          Latest recommendation sets generated from Agent sessions.
        </TextRegularM>
      </div>

      <div className={styles.PlaylistList} data-testid="sidebar-playlist-list">
        {isLoading ? <TextRegularM>Loading recommended playlists...</TextRegularM> : null}
        {!isLoading && !errorMessage && playlists.length === 0 ? (
          <TextRegularM>No recommendation playlists yet.</TextRegularM>
        ) : null}
        {errorMessage ? <TextRegularM>{errorMessage}</TextRegularM> : null}

        {playlists.map((playlist) => {
          const cover = playlist.tracks?.[0]?.track?.albumImageUrl || null
          const trackCount = playlist.tracks?.length ?? 0
          const isActive = activePlaylistId === playlist.id

          return (
            <button
              key={playlist.id}
              className={`${styles.PlaylistCard} ${isActive ? styles.ActivePlaylistCard : ''}`.trim()}
              type="button"
              onClick={() => handlePlaylistSelect(playlist)}
              data-testid="sidebar-playlist-card"
            >
              {cover ? (
                <img className={styles.PlaylistCardImage} src={cover} alt={playlist.name} />
              ) : (
                <span className={styles.PlaylistCardFallback} aria-hidden="true">
                  AM
                </span>
              )}

              <span className={styles.PlaylistCardContent}>
                <span className={styles.PlaylistCardTitle} title={playlist.name}>
                  {playlist.name}
                </span>
                <span className={styles.PlaylistCardMeta}>Agent playlist · {trackCount} tracks</span>
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

export default Playlist
