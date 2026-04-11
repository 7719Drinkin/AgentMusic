import * as Icons from '../icons'
import styles from './current-playing-menu.module.css'

function CurrentPlayingMenu({ trackName, onClose, onGoArtist }) {
  const menuLabel = trackName || '当前歌曲'

  return (
    <div className={styles.menu} role="menu" aria-label={`更多有关 ${menuLabel} 的选项`}>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>加入歌单</span>
        <Icons.Nextpage />
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>加入播放队列</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>推荐此歌曲类型歌单</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>推荐此歌手歌单</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem" onClick={onGoArtist}>
        <span>转至艺人</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>转至专辑</span>
      </button>
      <button className={styles.menuItem} type="button" role="menuitem">
        <span>查看制作人</span>
      </button>
      <button className={`${styles.menuItem} ${styles.secondaryItem}`} type="button" role="menuitem" onClick={onClose}>
        <span>关闭</span>
      </button>
    </div>
  )
}

export default CurrentPlayingMenu
