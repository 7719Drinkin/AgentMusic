import * as Icons from '../../icons'
import styles from './music-control-box.module.css'

function MusicControlBox({
  isPlaying,
  playbackMode,
  onTogglePlay,
  onPrevious,
  onNext,
  onToggleShuffle,
  onCycleLoopMode,
  disablePlay,
  disableSkip,
  disableModeToggle,
  isBusy,
}) {
  const shuffleActive = playbackMode === 'SHUFFLE'
  const loopActive = playbackMode === 'LIST_LOOP' || playbackMode === 'SINGLE_LOOP'

  return (
    <div className={styles.musicControl}>
      <button
        className={`${styles.button} ${shuffleActive ? styles.activeButton : ''}`}
        onClick={onToggleShuffle}
        type="button"
        disabled={disableModeToggle || isBusy}
        aria-label="切换随机播放"
      >
        <Icons.Mix />
        <span className={styles.tooltip}>随机播放</span>
      </button>
      <button
        className={styles.button}
        onClick={onPrevious}
        type="button"
        disabled={disableSkip || isBusy}
        aria-label="上一首"
      >
        <Icons.Prev />
        <span className={styles.tooltip}>上一首</span>
      </button>
      <button
        className={`${styles.button} ${styles.playButton}`}
        onClick={onTogglePlay}
        type="button"
        disabled={disablePlay || isBusy}
        aria-label={isPlaying ? '暂停' : '播放'}
      >
        {isPlaying ? <Icons.Pause /> : <Icons.Play />}
        <span className={styles.tooltip}>{isPlaying ? '暂停' : '播放'}</span>
      </button>
      <button
        className={styles.button}
        onClick={onNext}
        type="button"
        disabled={disableSkip || isBusy}
        aria-label="下一首"
      >
        <Icons.Next />
        <span className={styles.tooltip}>下一首</span>
      </button>
      <button
        className={`${styles.button} ${loopActive ? styles.activeButton : ''}`}
        onClick={onCycleLoopMode}
        type="button"
        disabled={disableModeToggle || isBusy}
        aria-label="切换循环模式"
      >
        <Icons.Loop />
        <span className={styles.tooltip}>循环模式</span>
      </button>
    </div>
  )
}

export default MusicControlBox
