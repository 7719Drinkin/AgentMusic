import * as Icons from '../icons'
import styles from './current-playing-menu.module.css'

function CurrentPlayingMenu({ trackName, onClose, onGoArtist }) {
  const menuLabel = trackName || 'current track'

  return (
    <div className={styles.menu} role="menu" aria-label={`More actions for ${menuLabel}`}>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>Add to playlist</span>
        <Icons.Nextpage />
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>Add to queue</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>Recommend similar tracks</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>Recommend this artist</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem" onClick={onGoArtist}>
        <span>Go to artist</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>Go to album</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>View credits</span>
      </button>
      <button className={`${styles.menuItem} ${styles.secondaryItem}`} type="button" role="menuitem" onClick={onClose}>
        <span>Close</span>
      </button>
    </div>
  )
}

export default CurrentPlayingMenu
