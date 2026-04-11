import React, { useRef, useEffect, useState } from 'react'
import { connect } from 'react-redux'
import { changeTrack, syncPlaybackSession as syncPlaybackSessionAction } from '../../actions'
import useWindowSize from '../../hooks/useWindowSize'
import FooterLeft from './footer-left'
import MusicControlBox from './player/music-control-box'
import MusicProgressBar from './player/music-progress-bar'
import FooterRight from './footer-right'
import Audio from './audio'
import {
  changePlaybackMode,
  fetchPlaybackSession,
  nextTrack,
  pausePlayback,
  playTrack,
  previousTrack,
  seekPlayback,
  syncPlaybackSession,
} from '../../api/playback'
import { fetchArtist, fetchTrack } from '../../api/music'
import { PLAYLIST } from '../../data/index'
import CONST from '../../constants/index'
import styles from './footer.module.css'

const DEMO_USER_ID = 'demo-user'
const PLAYBACK_REFRESH_EVENT = 'agentmusic:playback-session-updated'
const QUEUE_NEXT_REQUEST_EVENT = 'agentmusic:queue-next-request'

function Footer(props) {
  const size = useWindowSize()
  const footerRef = useRef(null)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [volume, setVolume] = useState(1)
  const [isPlaybackBusy, setIsPlaybackBusy] = useState(false)
  const audioRef = useRef(null)
  const hasTrackContext = Boolean(props.trackData.trackId || props.trackData.track)
  const hasPlaylistContext = props.trackData.trackKey[0] >= 0 || Boolean(props.currentPlaylistId)
  const canSkipTrack = hasTrackContext && hasPlaylistContext
  const canSeek = hasTrackContext && (duration || (props.trackData.durationMs || 0) / 1000) > 0

  const applyPlaybackSession = async (session) => {
    if (!session) {
      return
    }

    let payload = {
      currentPositionMs: session.currentPositionMs,
      isPlaying: session.isPlaying,
      playbackMode: session.playbackMode,
      deviceId: session.deviceId,
      currentPlaylistId: session.currentPlaylistId,
      currentTrackIndex: session.currentTrackIndex,
    }

    if (session.currentTrackId) {
      const track = await fetchTrack(session.currentTrackId)
      if (track) {
        let artistName = track.artistId || 'Spotify 曲目'

        if (track.artistId) {
          try {
            const artist = await fetchArtist(track.artistId)
            artistName = artist?.name || artistName
          } catch {
          }
        }

        payload = {
          ...payload,
          trackId: track.trackId,
          track: track.previewUrl || null,
          trackName: track.title,
          trackImg: track.albumImageUrl || props.trackData.trackImg,
          trackArtist: artistName,
          durationMs: track.durationMs,
        }
      }
    }

    props.syncPlaybackSessionAction(payload)
    setCurrentTime((session.currentPositionMs || 0) / 1000)
  }

  const refreshPlaybackSession = async (useSyncEndpoint = false) => {
    try {
      const session = useSyncEndpoint
        ? await syncPlaybackSession(DEMO_USER_ID)
        : await fetchPlaybackSession(DEMO_USER_ID)
      await applyPlaybackSession(session)
    } catch {
    }
  }

  useEffect(() => {
    if (!footerRef.current) {
      return undefined
    }

    const applyFooterHeight = () => {
      const nextHeight = Math.ceil(footerRef.current?.getBoundingClientRect().height || 0)
      if (nextHeight > 0) {
        document.documentElement.style.setProperty('--footer-safe-height', `${nextHeight}px`)
      }
    }

    applyFooterHeight()

    const observer = new ResizeObserver(() => {
      applyFooterHeight()
    })

    observer.observe(footerRef.current)
    window.addEventListener('resize', applyFooterHeight)

    return () => {
      observer.disconnect()
      window.removeEventListener('resize', applyFooterHeight)
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    const loadPlaybackSession = async () => {
      if (cancelled) {
        return
      }
      await refreshPlaybackSession(false)
    }

    loadPlaybackSession()
    window.addEventListener(PLAYBACK_REFRESH_EVENT, loadPlaybackSession)

    return () => {
      cancelled = true
      window.removeEventListener(PLAYBACK_REFRESH_EVENT, loadPlaybackSession)
    }
  }, [])

  useEffect(() => {
    if (!audioRef.current || !props.trackData.track) {
      if (audioRef.current) {
        audioRef.current.pause()
      }
      return
    }

    if (props.isPlaying) {
      audioRef.current.play().catch(() => {})
    } else {
      audioRef.current.pause()
    }
  }, [props.trackData.track, props.isPlaying])

  useEffect(() => {
    if (!audioRef.current) {
      return
    }

    const targetSeconds = (props.currentPositionMs || 0) / 1000
    if (Math.abs(audioRef.current.currentTime - targetSeconds) > 0.75) {
      audioRef.current.currentTime = targetSeconds
    }
  }, [props.currentPositionMs, props.trackData.trackId])

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume
    }
  }, [volume])

  const handleNext = async () => {
    if (!canSkipTrack || isPlaybackBusy) {
      return
    }

    if (!props.trackData.trackId) {
      if (props.trackData.trackKey[1] === PLAYLIST[props.trackData.trackKey[0]].playlistData.length - 1) {
        return
      }
      props.changeTrack([props.trackData.trackKey[0], parseInt(props.trackData.trackKey[1], 10) + 1])
      return
    }

    try {
      setIsPlaybackBusy(true)
      await nextTrack(DEMO_USER_ID, props.deviceId)
      await refreshPlaybackSession(true)
    } catch {
    } finally {
      setIsPlaybackBusy(false)
    }
  }

  const handlePrevious = async () => {
    if (!canSkipTrack || isPlaybackBusy) {
      return
    }

    if (!props.trackData.trackId) {
      if (props.trackData.trackKey[1] === 0) {
        return
      }
      props.changeTrack([props.trackData.trackKey[0], props.trackData.trackKey[1] - 1])
      return
    }

    try {
      setIsPlaybackBusy(true)
      await previousTrack(DEMO_USER_ID, props.deviceId)
      await refreshPlaybackSession(true)
    } catch {
    } finally {
      setIsPlaybackBusy(false)
    }
  }

  useEffect(() => {
    if (!audioRef.current) {
      return
    }

    const handleEnded = async () => {
      if (props.trackData.trackId) {
        await handleNext()
        return
      }

      if (props.trackData.trackKey[0] < 0) {
        return
      }

      if (props.trackData.trackKey[1] === PLAYLIST[props.trackData.trackKey[0]].playlistData.length - 1) {
        props.changeTrack([props.trackData.trackKey[0], 0])
      } else {
        props.changeTrack([props.trackData.trackKey[0], parseInt(props.trackData.trackKey[1], 10) + 1])
      }
    }

    audioRef.current.addEventListener('ended', handleEnded)
    return () => {
      audioRef.current?.removeEventListener('ended', handleEnded)
    }
  }, [props.trackData.trackKey, props.trackData.trackId, handleNext, props.changeTrack])

  useEffect(() => {
    const requestNext = () => {
      handleNext()
    }

    window.addEventListener(QUEUE_NEXT_REQUEST_EVENT, requestNext)
    return () => {
      window.removeEventListener(QUEUE_NEXT_REQUEST_EVENT, requestNext)
    }
  }, [handleNext])

  const handleTrackClick = async (position) => {
    if (!canSeek || isPlaybackBusy) {
      return
    }

    if (audioRef.current) {
      audioRef.current.currentTime = position
    }
    setCurrentTime(position)

    if (props.trackData.trackId) {
      try {
        setIsPlaybackBusy(true)
        const session = await seekPlayback(DEMO_USER_ID, Math.round(position * 1000), props.deviceId)
        await applyPlaybackSession(session)
      } catch {
      } finally {
        setIsPlaybackBusy(false)
      }
    }
  }

  const handleTogglePlay = async () => {
    if (!hasTrackContext || isPlaybackBusy) {
      return
    }

    if (!props.trackData.trackId) {
      props.syncPlaybackSessionAction({
        isPlaying: !props.isPlaying,
        currentPositionMs: Math.round(currentTime * 1000),
        playbackMode: props.playbackMode,
        deviceId: props.deviceId,
      })
      return
    }

    try {
      setIsPlaybackBusy(true)
      const session = props.isPlaying
        ? await pausePlayback(DEMO_USER_ID, props.deviceId)
        : await playTrack(DEMO_USER_ID, {
            trackId: props.trackData.trackId,
            playlistId: props.currentPlaylistId,
            trackIndex: props.currentTrackIndex,
            deviceId: props.deviceId,
            playbackMode: props.playbackMode,
          })
      await applyPlaybackSession(session)
    } catch {
    } finally {
      setIsPlaybackBusy(false)
    }
  }

  const handleToggleShuffle = async () => {
    const nextMode = props.playbackMode === 'SHUFFLE' ? 'SEQUENTIAL' : 'SHUFFLE'
    await updatePlaybackMode(nextMode)
  }

  const handleCycleLoopMode = async () => {
    let nextMode = 'LIST_LOOP'
    if (props.playbackMode === 'LIST_LOOP') {
      nextMode = 'SINGLE_LOOP'
    } else if (props.playbackMode === 'SINGLE_LOOP') {
      nextMode = 'SEQUENTIAL'
    }
    await updatePlaybackMode(nextMode)
  }

  const updatePlaybackMode = async (nextMode) => {
    if (!hasTrackContext || isPlaybackBusy) {
      return
    }

    if (!props.trackData.trackId) {
      props.syncPlaybackSessionAction({
        isPlaying: props.isPlaying,
        currentPositionMs: Math.round(currentTime * 1000),
        playbackMode: nextMode,
        deviceId: props.deviceId,
      })
      return
    }

    try {
      setIsPlaybackBusy(true)
      const session = await changePlaybackMode(DEMO_USER_ID, nextMode, props.deviceId)
      await applyPlaybackSession(session)
    } catch {
    } finally {
      setIsPlaybackBusy(false)
    }
  }

  return (
    <footer ref={footerRef} className={styles.footer}>
      <div className={styles.nowplayingbar}>
        <FooterLeft onOpenNowPlayingPanel={props.onOpenNowPlayingPanel} />
        <div className={styles.footerMid}>
          <MusicControlBox
            isPlaying={props.isPlaying}
            playbackMode={props.playbackMode}
            onTogglePlay={handleTogglePlay}
            onPrevious={handlePrevious}
            onNext={handleNext}
            onToggleShuffle={handleToggleShuffle}
            onCycleLoopMode={handleCycleLoopMode}
            disablePlay={!hasTrackContext}
            disableSkip={!canSkipTrack}
            disableModeToggle={!hasTrackContext}
            isBusy={isPlaybackBusy}
          />
          <MusicProgressBar
            currentTime={currentTime}
            duration={duration || (props.trackData.durationMs || 0) / 1000}
            handleTrackClick={handleTrackClick}
            disabled={!canSeek || isPlaybackBusy}
          />
          <Audio
            ref={audioRef}
            handleDuration={setDuration}
            handleCurrentTime={setCurrentTime}
            trackData={props.trackData}
            isPlaying={props.isPlaying}
          />
        </div>
        {size.width > CONST.MOBILE_SIZE ? (
          <FooterRight
            volume={volume}
            setVolume={setVolume}
            onOpenNowPlayingPanel={props.onOpenNowPlayingPanel}
            onToggleQueueDrawer={props.onToggleQueueDrawer}
            isNowPlayingOpen={props.isNowPlayingOpen}
            isQueueOpen={props.isQueueOpen}
            hasTrackContext={hasTrackContext}
          />
        ) : null}
      </div>
    </footer>
  )
}

const mapStateToProps = (state) => {
  return {
    trackData: state.trackData,
    isPlaying: state.isPlaying,
    currentPositionMs: state.currentPositionMs,
    playbackMode: state.playbackMode,
    deviceId: state.deviceId,
    currentPlaylistId: state.currentPlaylistId,
    currentTrackIndex: state.currentTrackIndex,
  }
}

export default connect(mapStateToProps, { changeTrack, syncPlaybackSessionAction })(Footer)
