import styles from './current-playing-queue-drawer.module.css'

function CurrentPlayingQueueDrawer({ isOpen, queueItems, playlistTitle, onClose }) {
  return (
    <section className={`${styles.drawer} ${isOpen ? styles.drawerOpen : ''}`} aria-hidden={!isOpen}>
      <div className={styles.drawerHeader}>
        <div>
          <p>播放队列</p>
          <h3>{playlistTitle}</h3>
        </div>
        <button type="button" onClick={onClose} aria-label="关闭队列">
          收起
        </button>
      </div>

      <div className={styles.drawerBody}>
        {queueItems.length > 0 ? (
          queueItems.map((item, index) => (
            <div className={styles.queueItem} key={`${item.songName}-${index}`}>
              <img src={item.songimg} alt={item.songName} />
              <div className={styles.queueMeta}>
                <strong>{item.songName}</strong>
                <span>{item.songArtist}</span>
              </div>
              <span className={styles.queueOrder}>{index === 0 ? '当前' : `#${index + 1}`}</span>
            </div>
          ))
        ) : (
          <div className={styles.emptyState}>当前没有可展示的播放队列。</div>
        )}
      </div>
    </section>
  )
}

export default CurrentPlayingQueueDrawer
