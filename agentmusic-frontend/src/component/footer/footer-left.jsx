import { connect } from 'react-redux'
import * as Icons from '../icons'
import IconButton from '../buttons/icon-button'
import styles from './footer-left.module.css'

function FooterLeft({ trackData, onToggleNowPlayingPanel }) {
  const hasTrack = Boolean(trackData.trackName)

  return (
    <div className={styles.footerLeft}>
      <button
        className={styles.coverButton}
        type="button"
        onClick={() => onToggleNowPlayingPanel?.('details')}
        aria-label="打开当前播放栏"
      >
        <ImgBox trackData={trackData} />
      </button>
      <SongDetails trackData={trackData} onOpenPanel={onToggleNowPlayingPanel} />
      <IconButton
        icon={<Icons.Like />}
        activeicon={<Icons.LikeActive />}
        tooltip="加入歌单"
        ariaLabel="加入歌单"
        disabled={!hasTrack}
      />
      <IconButton
        icon={<Icons.Corner />}
        activeicon={<Icons.Corner />}
        tooltip="打开当前播放栏"
        ariaLabel="打开当前播放栏"
        onClick={() => onToggleNowPlayingPanel?.('details')}
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

function SongDetails({ trackData, onOpenPanel }) {
  return (
    <div className={styles.songDetails}>
      <button className={styles.trackLink} type="button" onClick={() => onOpenPanel?.('details')}>
        {trackData.trackName || '暂未播放'}
      </button>
      <button className={styles.artistLink} type="button" onClick={() => onOpenPanel?.('details')}>
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
