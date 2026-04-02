import * as Icons from '../../icons'
import styles from "./music-control-box.module.css"

function MusicControlBox({
    isPlaying,
    playbackMode,
    onTogglePlay,
    onPrevious,
    onNext,
    onToggleShuffle,
    onCycleLoopMode
}){
    const shuffleActive = playbackMode === 'SHUFFLE'
    const loopActive = playbackMode === 'LIST_LOOP' || playbackMode === 'SINGLE_LOOP'

    return (
        <div className={styles.musicControl}>
            <button
                className={`${styles.button} ${shuffleActive ? styles.activeButton : ''}`}
                onClick={onToggleShuffle}
                type="button"
            >
                <Icons.Mix />
            </button>
            <button className={styles.button} onClick={onPrevious} type="button">
                <Icons.Prev />
            </button>
            <button className={`${styles.button} ${styles.playButton}`} onClick={onTogglePlay} type="button">
                {isPlaying ? <Icons.Pause /> : <Icons.Play />}
            </button>
            <button className={styles.button} onClick={onNext} type="button">
                <Icons.Next />
            </button>
            <button
                className={`${styles.button} ${loopActive ? styles.activeButton : ''}`}
                onClick={onCycleLoopMode}
                type="button"
            >
                <Icons.Loop />
            </button>
        </div>
    );
}

export default MusicControlBox;
