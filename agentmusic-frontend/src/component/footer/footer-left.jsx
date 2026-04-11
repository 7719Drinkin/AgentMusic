import { connect } from 'react-redux'
import { useHistory } from 'react-router-dom'
import * as Icons from '../icons'
import IconButton from '../buttons/icon-button'
import { buildArtistSearchLocation } from '../current-playing/current-playing-helpers'
import styles from './footer-left.module.css'

function FooterLeft({ trackData, onOpenNowPlayingPanel }) {
  const history = useHistory()
  const hasTrack = Boolean(trackData.trackName)

  const handleArtistClick = () => {
    if (!trackData.trackArtist) {
      return
    }

    history.push(
      buildArtistSearchLocation(trackData.trackArtist, {
        artistImage: trackData.trackImg,
        from: 'footer-player',
      }),
    )
  }

  return (
    <div className={styles.footerLeft}>
      <button
        className={styles.coverButton}
        type="button"
        onClick={() => onOpenNowPlayingPanel?.()}
        aria-label="打开当前播放栏"
      >
        <ImgBox trackData={trackData} />
      </button>
      <SongDetails trackData={trackData} onOpenPanel={onOpenNowPlayingPanel} onArtistClick={handleArtistClick} />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Like />}
        activeicon={<Icons.LikeActive />}
        tooltip="加入歌单"
        ariaLabel="加入歌单"
        disabled={!hasTrack}
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Corner />}
        activeicon={<Icons.Corner />}
        tooltip="打开当前播放栏"
        ariaLabel="打开当前播放栏"
        onClick={() => onOpenNowPlayingPanel?.()}
        toggleOnClick={false}
        disabled={!hasTrack}
      />
    </div>
  )
}

function ImgBox({ trackData }) {
  const coverSrc = trackData.trackImg || '/image/Playlist/liked-songs.PNG'

  return (
    <div className={styles.imgBox}>
      <img src={coverSrc} alt={trackData.trackName || '当前曲目封面'} />
    </div>
  )
}

function SongDetails({ trackData, onOpenPanel, onArtistClick }) {
  return (
    <div className={styles.songDetails}>
      <button className={styles.trackLink} type="button" onClick={() => onOpenPanel?.()}>
        {trackData.trackName || '暂无播放'}
      </button>
      <button className={styles.artistLink} type="button" onClick={onArtistClick}>
        {trackData.trackArtist || '等待推荐'}
      </button>
    </div>
  )
}

const mapStateToProps = (state) => {
  return {
    trackData: state.trackData,
  }
}

export default connect(mapStateToProps)(FooterLeft)
