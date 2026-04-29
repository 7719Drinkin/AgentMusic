import { useEffect, useRef, useState } from 'react'
import * as Icons from '../icons'
import RangeSlider from './range-slider'
import IconButton from '../buttons/icon-button'
import styles from './footer-right.module.css'

function FooterRight({
  volume,
  setVolume,
  onOpenNowPlayingPanel,
  onToggleQueueDrawer,
  isNowPlayingOpen,
  isQueueOpen,
  hasTrackContext,
  devices,
  currentDeviceId,
  isDevicesLoading,
  isDeviceBusy,
  devicePanelMessage,
  devicePanelTone,
  onRefreshDevices,
  onTransferDevice,
}) {
  const [isDevicePanelOpen, setIsDevicePanelOpen] = useState(false)
  const [pendingDeviceId, setPendingDeviceId] = useState(null)
  const devicePanelRef = useRef(null)
  const currentDevice = devices.find((device) => device.active || device.id === currentDeviceId) || null
  const currentDeviceName = currentDevice?.name || ''
  const hasRestrictedOnly = !isDevicesLoading && devices.length > 0 && devices.every((device) => device.restricted)
  const hasCurrentDeviceMissing = !isDevicesLoading && Boolean(currentDeviceId) && !currentDevice

  useEffect(() => {
    if (!isDevicePanelOpen) {
      return undefined
    }

    const handlePointerDown = (event) => {
      if (!devicePanelRef.current?.contains(event.target)) {
        setIsDevicePanelOpen(false)
      }
    }

    window.addEventListener('pointerdown', handlePointerDown)
    return () => {
      window.removeEventListener('pointerdown', handlePointerDown)
    }
  }, [isDevicePanelOpen])

  const handleToggleDevices = async () => {
    const nextOpen = !isDevicePanelOpen
    setIsDevicePanelOpen(nextOpen)
    if (nextOpen) {
      await onRefreshDevices?.()
    }
  }

  const handleSelectDevice = async (deviceId) => {
    if (!deviceId) {
      return
    }
    setPendingDeviceId(deviceId)
    const switched = await onTransferDevice?.(deviceId)
    setPendingDeviceId(null)
    if (switched) {
      setIsDevicePanelOpen(false)
    }
  }

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
      <div ref={devicePanelRef} className={styles.devicePanelWrap}>
        <button
          className={`${styles.deviceSummaryButton} ${isDevicePanelOpen ? styles.deviceSummaryButtonActive : ''}`.trim()}
          type="button"
          onClick={handleToggleDevices}
          aria-label="Playback devices"
          data-testid="playback-device-toggle"
        >
          <span className={styles.deviceSummaryIcon}>
            <Icons.Devices />
          </span>
          <span className={styles.deviceSummaryTextWrap}>
            <span className={styles.deviceSummaryLabel}>Device</span>
            <span className={styles.deviceSummaryText} data-testid="playback-device-summary">
              {currentDeviceName || (isDevicesLoading ? 'Refreshing...' : 'No active device')}
            </span>
          </span>
        </button>
        {isDevicePanelOpen ? (
          <div className={styles.devicePanel} data-testid="playback-device-panel">
            <div className={styles.devicePanelHeader}>
              <div>
                <p className={styles.devicePanelTitle}>Playback devices</p>
                <p className={styles.devicePanelMeta}>
                  {devices.length} available
                  {currentDeviceId ? ' · current device ready' : ''}
                </p>
              </div>
              <button
                className={styles.refreshButton}
                type="button"
                onClick={onRefreshDevices}
                disabled={isDevicesLoading || isDeviceBusy}
                data-testid="playback-device-refresh"
              >
                {isDevicesLoading ? 'Refreshing...' : 'Refresh'}
              </button>
            </div>
            {devicePanelMessage ? (
              <p
                className={`${styles.deviceNotice} ${devicePanelTone === 'error' ? styles.deviceNoticeError : styles.deviceNoticeSuccess}`.trim()}
                data-testid="playback-device-notice"
              >
                {devicePanelMessage}
              </p>
            ) : null}
            {!devicePanelMessage && hasCurrentDeviceMissing ? (
              <p className={`${styles.deviceNotice} ${styles.deviceNoticeError}`.trim()} data-testid="playback-device-notice">
                Current session device is offline. Choose another available device.
              </p>
            ) : null}
            {!devicePanelMessage && hasRestrictedOnly ? (
              <p className={`${styles.deviceNotice} ${styles.deviceNoticeError}`.trim()} data-testid="playback-device-notice">
                Detected devices are restricted. Keep the same bridge account Web Player or client active.
              </p>
            ) : null}
            <div className={styles.devicePanelBody}>
              {isDevicesLoading ? (
                <p className={styles.deviceEmpty} data-testid="playback-device-loading">Loading devices...</p>
              ) : null}
              {!isDevicesLoading && devices.length === 0 ? (
                <p className={styles.deviceEmpty} data-testid="playback-device-empty">
                  No available Spotify devices. Keep the same bridge account Web Player or desktop client online.
                </p>
              ) : null}
              {!isDevicesLoading && devices.map((device) => {
                const isActive = device.active || device.id === currentDeviceId
                const isRestricted = Boolean(device.restricted)
                const isPending = pendingDeviceId === device.id
                return (
                  <button
                    key={device.id}
                    className={`${styles.deviceItem} ${isActive ? styles.deviceItemActive : ''}`.trim()}
                    type="button"
                    onClick={() => handleSelectDevice(device.id)}
                    disabled={isActive || isRestricted || isDeviceBusy}
                    data-testid="playback-device-item"
                    data-current={isActive ? 'true' : 'false'}
                    data-device-id={device.id}
                    data-restricted={isRestricted ? 'true' : 'false'}
                  >
                    <span className={styles.deviceItemMain}>
                      <span className={styles.deviceName} data-testid="playback-device-name">{device.name}</span>
                      <span className={styles.deviceType}>{device.type}</span>
                    </span>
                    <span className={styles.deviceStatus}>
                      {isRestricted ? 'Restricted' : isPending ? 'Switching...' : isActive ? 'Current' : 'Switch'}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        ) : null}
      </div>
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
