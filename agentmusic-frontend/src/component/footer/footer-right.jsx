import { useState } from 'react'
import * as Icons from '../icons'
import RangeSlider from './range-slider'
import IconButton from '../buttons/icon-button'
import styles from './footer-right.module.css'

function FooterRight({ volume, setVolume, onOpenNowPlayingPanel, onToggleQueueDrawer, isNowPlayingOpen, isQueueOpen, hasTrackContext }) {
  return (
    <div className={styles.footerRight}>
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Lyrics />}
        tooltip="歌词（稍后实现）"
        ariaLabel="歌词（稍后实现）"
        toggleOnClick={false}
        disabled
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Queue />}
        active={isNowPlayingOpen && isQueueOpen}
        tooltip="打开队列"
        ariaLabel="打开队列"
        onClick={onToggleQueueDrawer}
        toggleOnClick={false}
        disabled={!hasTrackContext}
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.MiniPlayer />}
        tooltip="迷你播放器（Priority 2）"
        ariaLabel="迷你播放器（Priority 2）"
        toggleOnClick={false}
        disabled
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Corner />}
        active={isNowPlayingOpen && !isQueueOpen}
        tooltip="打开当前播放栏"
        ariaLabel="打开当前播放栏"
        onClick={onOpenNowPlayingPanel}
        toggleOnClick={false}
        disabled={!hasTrackContext}
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
