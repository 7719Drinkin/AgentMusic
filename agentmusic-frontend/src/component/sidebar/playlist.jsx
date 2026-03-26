import { useEffect, useState } from 'react'
import styles from './playlist.module.css'
import TitleS from '../text/title-s'
import TextRegularM from '../text/text-regular-m'
import PlaylistButton from './playlist-button'
import { PLAYLISTBTN } from '../../constants'
import { fetchRecentPlaylists } from '../../api/playlists'

const DEMO_USER_ID = 'demo-user'
const PLAYLIST_REFRESH_EVENT = 'agentmusic:playlists-updated'

function Playlist() {
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
          <div key={playlist.id} className={styles.PlaylistItem}>
            <TextRegularM>{playlist.name}</TextRegularM>
          </div>
        ))}
      </div>
    </div>
  )
}

export default Playlist
