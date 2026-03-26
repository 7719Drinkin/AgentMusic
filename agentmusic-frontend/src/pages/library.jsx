import { Route } from 'react-router-dom'
import TitleM from '../component/text/title-m'
import Topnav from '../component/topnav/topnav'
import PlaylistCardM from '../component/cards/playlist-card-m'
import { PLAYLIST } from '../data/index'
import styles from './library.module.css'

function Library() {
  return (
    <div className={styles.LibPage}>
      <Topnav tabButtons={true} />
      <div className={styles.Library}>
        <Route exact path="/library">
          <PlaylistTab />
        </Route>
        <Route path="/library/podcasts">
          <PodcastTab />
        </Route>
        <Route path="/library/artists">
          <ArtistTab />
        </Route>
        <Route path="/library/albums">
          <AlbumTab />
        </Route>
      </div>
    </div>
  )
}

function PlaylistTab() {
  return (
    <div>
      <TitleM>歌单</TitleM>
      <div className={styles.Grid}>
        {PLAYLIST.filter((item) => item.type === 'playlist').map((item) => (
          <PlaylistCardM key={item.title} data={item} />
        ))}
      </div>
    </div>
  )
}

function PodcastTab() {
  return (
    <div>
      <TitleM>播客</TitleM>
      <div className={styles.Grid}>
        {PLAYLIST.filter((item) => item.type === 'podcast').map((item) => (
          <PlaylistCardM key={item.title} data={item} />
        ))}
      </div>
    </div>
  )
}

function ArtistTab() {
  return (
    <div>
      <TitleM>歌手</TitleM>
    </div>
  )
}

function AlbumTab() {
  return (
    <div>
      <TitleM>专辑</TitleM>
      <div className={styles.Grid}>
        {PLAYLIST.filter((item) => item.type === 'album').map((item) => (
          <PlaylistCardM key={item.title} data={item} />
        ))}
      </div>
    </div>
  )
}

export default Library
