import { useEffect, useRef, useState } from 'react'
import * as Icons from '../icons'
import IconButton from '../buttons/icon-button'
import RangeSlider from './range-slider'
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
  webPlayback,
  onEnableWebPlayback,
  onRefreshDevices,
  onTransferDevice,
}) {
  const [isDevicePanelOpen, setIsDevicePanelOpen] = useState(false)
  const [pendingDeviceId, setPendingDeviceId] = useState(null)
  const devicePanelRef = useRef(null)
  const currentDevice = devices.find((device) => device.active || device.id === currentDeviceId) || null
  const hasRestrictedOnly = !isDevicesLoading && devices.length > 0 && devices.every((device) => device.restricted)
  const hasCurrentDeviceMissing = !isDevicesLoading && Boolean(currentDeviceId) && !currentDevice
  const deviceSummaryState = getDeviceSummaryState({
    isDevicesLoading,
    devicePanelMessage,
    devicePanelTone,
    hasRestrictedOnly,
    hasCurrentDeviceMissing,
    hasCurrentDevice: Boolean(currentDevice),
  })
  const deviceSummaryStateClassName = getSummaryToneClassName(styles, deviceSummaryState.tone)
  const panelNoticeClassName = getNoticeToneClassName(styles, devicePanelTone)
  const webPlayerState = getWebPlayerState(webPlayback)
  const webPlayerToneClassName = getNoticeToneClassName(styles, webPlayerState.tone)

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
        tooltip="Lyrics (later)"
        ariaLabel="Lyrics (later)"
        toggleOnClick={false}
        disabled
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.Queue />}
        active={isNowPlayingOpen && isQueueOpen}
        tooltip="Open queue"
        ariaLabel="Open queue"
        onClick={onToggleQueueDrawer}
        toggleOnClick={false}
        disabled={!hasTrackContext}
      />
      <IconButton
        className={styles.footerIcon}
        icon={<Icons.MiniPlayer />}
        tooltip="Mini player (Priority 2)"
        ariaLabel="Mini player (Priority 2)"
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
          <span
            className={`${styles.deviceSummaryIcon} ${styles.deviceSummaryIconButton} ${deviceSummaryStateClassName}`.trim()}
            data-testid="playback-device-summary"
          >
            <Icons.Devices />
          </span>
        </button>
        {isDevicePanelOpen ? (
          <div className={styles.devicePanel} data-testid="playback-device-panel">
            <div className={styles.devicePanelHeader}>
              <div>
                <p className={styles.devicePanelTitle}>Playback devices</p>
                <div className={styles.devicePanelMetaRow}>
                  <p className={styles.devicePanelMeta}>{devices.length} available</p>
                  {currentDeviceId ? (
                    <span className={styles.devicePanelMetaBadge}>Current session tracked</span>
                  ) : null}
                </div>
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
            <div className={styles.webPlayerCard} data-testid="agentmusic-web-player-card">
              <div className={styles.webPlayerMain}>
                <span className={styles.webPlayerTitle}>AgentMusic Web Player</span>
                <span className={`${styles.webPlayerStatus} ${webPlayerToneClassName}`.trim()}>
                  {webPlayerState.label}
                </span>
                {webPlayback?.errorMessage ? (
                  <span className={`${styles.webPlayerHint} ${webPlayerToneClassName}`.trim()}>
                    {webPlayback.errorMessage}
                  </span>
                ) : null}
              </div>
              <button
                className={styles.webPlayerButton}
                type="button"
                onClick={onEnableWebPlayback}
                disabled={isDeviceBusy || webPlayback?.isConnecting}
                data-testid="agentmusic-web-player-enable"
              >
                {webPlayback?.isConnecting ? 'Connecting...' : webPlayback?.isReady ? 'Use here' : 'Enable'}
              </button>
            </div>
            {devicePanelMessage ? (
              <p
                className={`${styles.deviceNotice} ${panelNoticeClassName}`.trim()}
                data-testid="playback-device-notice"
              >
                {devicePanelMessage}
              </p>
            ) : null}
            {!devicePanelMessage && hasCurrentDeviceMissing ? (
              <p
                className={`${styles.deviceNotice} ${styles.deviceNoticeWarning}`.trim()}
                data-testid="playback-device-notice"
              >
                Current session device is offline. Enable AgentMusic Web Player or choose another available device.
              </p>
            ) : null}
            {!devicePanelMessage && hasRestrictedOnly ? (
              <p
                className={`${styles.deviceNotice} ${styles.deviceNoticeWarning}`.trim()}
                data-testid="playback-device-notice"
              >
                Detected devices are restricted. Enable AgentMusic Web Player or choose another available device.
              </p>
            ) : null}
            <div className={styles.devicePanelBody}>
              {isDevicesLoading ? (
                <p className={styles.deviceEmpty} data-testid="playback-device-loading">Loading devices...</p>
              ) : null}
              {!isDevicesLoading && devices.length === 0 ? (
                <p className={styles.deviceEmpty} data-testid="playback-device-empty">
                  No available Spotify devices. Enable AgentMusic Web Player to play in this browser.
                </p>
              ) : null}
              {!isDevicesLoading && devices.map((device) => {
                const isActive = device.active || device.id === currentDeviceId
                const isRestricted = Boolean(device.restricted)
                const isPending = pendingDeviceId === device.id
                const statusClassName = isRestricted
                  ? styles.deviceStatusWarning
                  : isPending
                    ? styles.deviceStatusAction
                    : isActive
                      ? styles.deviceStatusCurrent
                      : styles.deviceStatusMuted

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
                      <span className={styles.deviceTitleRow}>
                        <span className={styles.deviceName} data-testid="playback-device-name">{device.name}</span>
                        {isActive ? <span className={styles.deviceChip}>Current</span> : null}
                      </span>
                      <span className={styles.deviceType}>{device.type}</span>
                    </span>
                    <span className={`${styles.deviceStatus} ${statusClassName}`.trim()}>
                      {isRestricted ? 'Restricted' : isPending ? 'Switching...' : isActive ? 'Current device' : 'Available'}
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
        tooltip="Open now playing panel"
        ariaLabel="Open now playing panel"
        onClick={onOpenNowPlayingPanel}
        toggleOnClick={false}
        disabled={!hasTrackContext}
      />
      <SoundLevel volume={volume} setVolume={setVolume} />
    </div>
  )
}

function getDeviceSummaryState({
  isDevicesLoading,
  devicePanelMessage,
  devicePanelTone,
  hasRestrictedOnly,
  hasCurrentDeviceMissing,
  hasCurrentDevice,
}) {
  if (isDevicesLoading) {
    return { text: 'Refreshing device list', tone: 'info' }
  }
  if (devicePanelMessage && devicePanelTone === 'success') {
    return { text: 'Playback device updated', tone: 'success' }
  }
  if (devicePanelMessage && devicePanelTone === 'warning') {
    return { text: 'Device action required', tone: 'warning' }
  }
  if (devicePanelMessage && devicePanelTone === 'error') {
    return { text: 'Playback routing needs attention', tone: 'error' }
  }
  if (hasCurrentDeviceMissing) {
    return { text: 'Last session device is offline', tone: 'warning' }
  }
  if (hasRestrictedOnly) {
    return { text: 'Detected devices are restricted', tone: 'warning' }
  }
  if (hasCurrentDevice) {
    return { text: 'Current device ready', tone: 'success' }
  }
  return { text: 'Enable AgentMusic Web Player', tone: 'info' }
}

function getWebPlayerState(webPlayback) {
  if (webPlayback?.isConnecting) {
    return { label: 'Connecting', tone: 'info' }
  }
  if (webPlayback?.isActive) {
    return { label: 'Playing here', tone: 'success' }
  }
  if (webPlayback?.isReady) {
    return { label: 'Ready', tone: 'success' }
  }
  if (webPlayback?.errorMessage) {
    return { label: 'Needs attention', tone: 'error' }
  }
  return { label: 'Off', tone: 'info' }
}

function getNoticeToneClassName(stylesModule, tone) {
  switch (tone) {
    case 'success':
      return stylesModule.deviceNoticeSuccess
    case 'warning':
      return stylesModule.deviceNoticeWarning
    case 'error':
      return stylesModule.deviceNoticeError
    default:
      return stylesModule.deviceNoticeInfo
  }
}

function getSummaryToneClassName(stylesModule, tone) {
  switch (tone) {
    case 'success':
      return stylesModule.deviceSummarySuccess
    case 'warning':
      return stylesModule.deviceSummaryWarning
    case 'error':
      return stylesModule.deviceSummaryError
    default:
      return stylesModule.deviceSummaryInfo
  }
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
        tooltip={volume === 0 ? 'Restore volume' : 'Mute'}
        ariaLabel={volume === 0 ? 'Restore volume' : 'Mute'}
        onClick={soundBtn}
        toggleOnClick={false}
        active={volume === 0}
      />
      <RangeSlider minvalue={0} maxvalue={1} value={volume} handleChange={setVolume} />
    </div>
  )
}

export default FooterRight
