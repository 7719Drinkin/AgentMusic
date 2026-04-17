import { useEffect, useMemo, useState } from 'react'
import { useHistory, useLocation } from 'react-router-dom'
import styles from './playlist.module.css'
import TitleS from '../text/title-s'
import TextRegularM from '../text/text-regular-m'
import PlaylistButton from './playlist-button'
import { PLAYLISTBTN } from '../../constants'
import { fetchRecentPlaylists } from '../../api/playlists'

const DEMO_USER_ID = 'demo-user'
const PLAYLIST_REFRESH_EVENT = 'agentmusic:playlists-updated'
const MAX_PLAYLIST_TITLE_LENGTH = 20

function truncatePlaylistTitle(title) {
  if (!title) {
    return ''
  }

  if (title.length <= MAX_PLAYLIST_TITLE_LENGTH) {
    return title
  }

  return `${title.slice(0, MAX_PLAYLIST_TITLE_LENGTH - 1)}…`
}

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

        setErrorMessage(error.message || '\u63a8\u8350\u6b4c\u5355\u52a0\u8f7d\u5931\u8d25\u3002')
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
      <TitleS>{'\u63a8\u8350\u6b4c\u5355'}</TitleS>

      <div className={styles.FixedItems}>
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
        {isLoading ? <TextRegularM>{'\u6b63\u5728\u52a0\u8f7d\u63a8\u8350\u6b4c\u5355...'}</TextRegularM> : null}
        {!isLoading && !errorMessage && playlists.length === 0 ? (
          <TextRegularM>{'\u8fd8\u6ca1\u6709\u751f\u6210\u8fc7\u63a8\u8350\u6b4c\u5355\u3002'}</TextRegularM>
        ) : null}
        {errorMessage ? <TextRegularM>{errorMessage}</TextRegularM> : null}

        {playlists.map((playlist) => {
          const cover = playlist.tracks?.[0]?.track?.albumImageUrl || null
          const trackCount = playlist.tracks?.length ?? 0
          const isActive = activePlaylistId === playlist.id

          return (
            <button
              key={playlist.id}
              className={`${styles.PlaylistCard} ${isActive ? styles.ActivePlaylistCard : ''}`}
              type="button"
              onClick={() => handlePlaylistSelect(playlist)}
            >
              {cover ? (
                <img className={styles.PlaylistCardImage} src={cover} alt={playlist.name} />
              ) : (
                <span className={styles.PlaylistCardFallback} aria-hidden="true">
                  AR
                </span>
              )}

              <span className={styles.PlaylistCardContent}>
                <span className={styles.PlaylistCardTitle}>{truncatePlaylistTitle(playlist.name)}</span>
                <span className={styles.PlaylistCardMeta}>
                  {'\u63a8\u8350\u6b4c\u5355 \u00b7 '}
                  {trackCount}
                  {' \u9996'}
                </span>
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

export default Playlist
