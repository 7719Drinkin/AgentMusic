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
        aria-label="Open now playing view"
      >
        <ImgBox trackData={trackData} />
      </button>
      <SongDetails trackData={trackData} onOpenPanel={onOpenNowPlayingPanel} onArtistClick={handleArtistClick} />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Like />}
        activeicon={<Icons.LikeActive />}
        tooltip="Save to playlist"
        ariaLabel="Save to playlist"
        disabled={!hasTrack}
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Corner />}
        activeicon={<Icons.Corner />}
        tooltip="Open now playing view"
        ariaLabel="Open now playing view"
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
      <img src={coverSrc} alt={trackData.trackName || 'Current track cover'} />
    </div>
  )
}

function SongDetails({ trackData, onOpenPanel, onArtistClick }) {
  return (
    <div className={styles.songDetails}>
      <button className={styles.trackLink} type="button" onClick={() => onOpenPanel?.()}>
        {trackData.trackName || 'Nothing playing yet'}
      </button>
      <button className={styles.artistLink} type="button" onClick={onArtistClick}>
        {trackData.trackArtist || 'Waiting for recommendation'}
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
