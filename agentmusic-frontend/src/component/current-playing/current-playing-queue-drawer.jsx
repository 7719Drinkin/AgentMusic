import styles from './current-playing-queue-drawer.module.css'

function CurrentPlayingQueueDrawer({ isOpen, queueItems, playlistTitle, onClose, onSelectQueueItem }) {
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
            <button
              type="button"
              className={`${styles.queueItem} ${item.isCurrent ? styles.queueItemCurrent : ''}`}
              key={`${item.trackId}-${index}`}
              onClick={() => onSelectQueueItem?.(item)}
              disabled={item.isCurrent}
            >
              <img src={item.songimg} alt={item.songName} />
              <div className={styles.queueMeta}>
                <strong>{item.songName}</strong>
                <span>{item.songArtist}</span>
              </div>
              <span className={styles.queueOrder}>{item.isCurrent ? '当前' : `#${index + 1}`}</span>
            </button>
          ))
        ) : (
          <div className={styles.emptyState}>当前没有可显示的播放队列。</div>
        )}
      </div>
    </section>
  )
}

export default CurrentPlayingQueueDrawer
