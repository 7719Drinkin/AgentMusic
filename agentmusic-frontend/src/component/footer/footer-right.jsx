import { useState } from 'react'
import * as Icons from '../icons'
import RangeSlider from './range-slider'
import IconButton from '../buttons/icon-button'
import styles from './footer-right.module.css'

function FooterRight({ volume, setVolume, onOpenNowPlayingPanel, currentPanelView, isNowPlayingOpen, hasTrackContext }) {
  return (
    <div className={styles.footerRight}>
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Lyrics />}
        active={isNowPlayingOpen && currentPanelView === 'lyrics'}
        tooltip="歌词"
        ariaLabel="打开歌词面板"
        onClick={() => onOpenNowPlayingPanel?.('lyrics')}
        toggleOnClick={false}
        disabled={!hasTrackContext}
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Queue />}
        active={isNowPlayingOpen && currentPanelView === 'queue'}
        tooltip="队列"
        ariaLabel="打开播放队列"
        onClick={() => onOpenNowPlayingPanel?.('queue')}
        toggleOnClick={false}
        disabled={!hasTrackContext}
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.MiniPlayer />}
        tooltip="迷你播放器（Priority 2）"
        ariaLabel="迷你播放器"
        toggleOnClick={false}
        disabled
      />
      <SoundLevel volume={volume} setVolume={setVolume} />
    </div>
  )
}

function SoundLevel({ volume, setVolume }) {
  const [lastVolume, setLastVolume] = useState(1)

  const soundBtn = () => {
    if (volume === 0) {
      setVolume(lastVolume)
    } else {
      setLastVolume(volume)
      setVolume(0)
    }
  }

  return (
    <div className={styles.soundBar}>
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Sound />}
        activeicon={<Icons.SoundClose />}
        tooltip={volume === 0 ? '恢复音量' : '静音'}
        ariaLabel={volume === 0 ? '恢复音量' : '静音'}
        onClick={soundBtn}
        toggleOnClick={false}
        active={volume === 0}
      />
      <RangeSlider minvalue={0} maxvalue={1} value={volume} handleChange={setVolume} />
    </div>
  )
}

export default FooterRight
