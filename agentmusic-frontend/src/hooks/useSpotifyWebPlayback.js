import { useEffect, useRef, useState } from 'react'
import { fetchWebPlaybackToken } from '../api/spotifyAuth'

const SDK_SCRIPT_ID = 'spotify-web-playback-sdk'
const SDK_SCRIPT_URL = 'https://sdk.scdn.co/spotify-player.js'
const PLAYER_NAME = 'AgentMusic Web Player'
const SDK_LOAD_TIMEOUT_MS = 15000
const READY_TIMEOUT_MS = 15000
const WEB_PLAYBACK_ENABLED_KEY = 'agentmusic:web-playback-enabled'

let sdkLoadPromise = null

function loadSpotifyPlaybackSdk() {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Spotify Web Playback SDK is only available in the browser.'))
  }

  if (window.Spotify?.Player) {
    return Promise.resolve(window.Spotify)
  }

  if (sdkLoadPromise) {
    return sdkLoadPromise
  }

  sdkLoadPromise = new Promise((resolve, reject) => {
    const existingScript = document.getElementById(SDK_SCRIPT_ID)
    const previousReadyHandler = window.onSpotifyWebPlaybackSDKReady
    const loadTimeoutId = window.setTimeout(() => {
      sdkLoadPromise = null
      reject(new Error('Timed out loading Spotify Web Playback SDK.'))
    }, SDK_LOAD_TIMEOUT_MS)

    window.onSpotifyWebPlaybackSDKReady = () => {
      previousReadyHandler?.()
      window.clearTimeout(loadTimeoutId)
      if (window.Spotify?.Player) {
        resolve(window.Spotify)
      } else {
        reject(new Error('Spotify Web Playback SDK loaded without Spotify.Player.'))
      }
    }

    if (existingScript) {
      return
    }

    const script = document.createElement('script')
    script.id = SDK_SCRIPT_ID
    script.src = SDK_SCRIPT_URL
    script.async = true
    script.onerror = () => {
      window.clearTimeout(loadTimeoutId)
      sdkLoadPromise = null
      reject(new Error('Failed to load Spotify Web Playback SDK.'))
    }
    document.body.appendChild(script)
  })

  return sdkLoadPromise
}

function createReadyTimeout() {
  return new Promise((_, reject) => {
    window.setTimeout(() => {
      reject(new Error('AgentMusic web player did not become ready in time.'))
    }, READY_TIMEOUT_MS)
  })
}

function normalizeSpotifyState(state) {
  if (!state) {
    return null
  }
  const currentTrack = state.track_window?.current_track ?? null
  return {
    paused: Boolean(state.paused),
    positionMs: state.position ?? 0,
    durationMs: state.duration ?? currentTrack?.duration_ms ?? 0,
    track: currentTrack
      ? {
          id: currentTrack.id,
          name: currentTrack.name,
          artistName: currentTrack.artists?.map((artist) => artist.name).filter(Boolean).join(', ') ?? '',
          albumName: currentTrack.album?.name ?? '',
          albumImageUrl: currentTrack.album?.images?.[0]?.url ?? '',
          durationMs: currentTrack.duration_ms ?? state.duration ?? 0,
        }
      : null,
  }
}

function toSdkErrorMessage(error) {
  if (typeof error === 'string') {
    return error
  }
  if (error?.message) {
    return error.message
  }
  return 'AgentMusic web player is unavailable.'
}

function toActionablePlaybackError(message) {
  const normalizedMessage = typeof message === 'string' ? message.trim() : ''
  if (!normalizedMessage || normalizedMessage.toLowerCase() === 'playback error') {
    return ''
  }
  return normalizedMessage
}

function rememberWebPlaybackEnabled() {
  try {
    window.sessionStorage?.setItem(WEB_PLAYBACK_ENABLED_KEY, 'true')
  } catch {
  }
}

function wasWebPlaybackEnabled() {
  try {
    return window.sessionStorage?.getItem(WEB_PLAYBACK_ENABLED_KEY) === 'true'
  } catch {
    return false
  }
}

export default function useSpotifyWebPlayback({ volume = 1, autoReconnect = false } = {}) {
  const playerRef = useRef(null)
  const deviceIdRef = useRef(null)
  const readyPromiseRef = useRef(null)
  const readyResolverRef = useRef(null)
  const readyRejecterRef = useRef(null)
  const autoReconnectAttemptedRef = useRef(false)
  const [deviceId, setDeviceId] = useState(null)
  const [isReady, setIsReady] = useState(false)
  const [isConnecting, setIsConnecting] = useState(false)
  const [isActive, setIsActive] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [playbackState, setPlaybackState] = useState(null)

  const resetReadyPromise = () => {
    readyPromiseRef.current = null
    readyResolverRef.current = null
    readyRejecterRef.current = null
  }

  const ensureReadyPromise = () => {
    if (!readyPromiseRef.current) {
      readyPromiseRef.current = Promise.race([
        new Promise((resolve, reject) => {
          readyResolverRef.current = resolve
          readyRejecterRef.current = reject
        }),
        createReadyTimeout(),
      ])
    }
    return readyPromiseRef.current
  }

  const createPlayer = async () => {
    if (playerRef.current) {
      return playerRef.current
    }

    const Spotify = await loadSpotifyPlaybackSdk()
    const player = new Spotify.Player({
      name: PLAYER_NAME,
      volume,
      getOAuthToken: async (callback) => {
        try {
          const token = await fetchWebPlaybackToken()
          callback(token.accessToken)
        } catch (error) {
          setErrorMessage(toSdkErrorMessage(error))
          readyRejecterRef.current?.(error)
          resetReadyPromise()
        }
      },
    })

    player.addListener('ready', ({ device_id: readyDeviceId }) => {
      deviceIdRef.current = readyDeviceId
      rememberWebPlaybackEnabled()
      setDeviceId(readyDeviceId)
      setIsReady(true)
      setIsConnecting(false)
      setErrorMessage('')
      readyResolverRef.current?.(readyDeviceId)
    })

    player.addListener('not_ready', ({ device_id: offlineDeviceId }) => {
      if (!deviceIdRef.current || deviceIdRef.current === offlineDeviceId) {
        resetReadyPromise()
        setIsReady(false)
        setIsActive(false)
        setErrorMessage('AgentMusic web player is offline. Reconnect it before playback.')
      }
    })

    player.addListener('player_state_changed', (state) => {
      const normalizedState = normalizeSpotifyState(state)
      setPlaybackState(normalizedState)
      setIsActive(Boolean(normalizedState && !normalizedState.paused))
    })

    const handleFatalSdkError = ({ message }) => {
      resetReadyPromise()
      setIsConnecting(false)
      setErrorMessage(message)
    }

    player.addListener('initialization_error', handleFatalSdkError)
    player.addListener('authentication_error', handleFatalSdkError)
    player.addListener('account_error', handleFatalSdkError)
    player.addListener('playback_error', ({ message }) => {
      const actionableMessage = toActionablePlaybackError(message)
      if (actionableMessage) {
        setErrorMessage(actionableMessage)
      }
    })
    player.addListener('autoplay_failed', () => {
      setErrorMessage('Browser blocked autoplay. Click play again to activate AgentMusic web player.')
    })

    playerRef.current = player
    return player
  }

  const ensureReady = async ({ activate = false } = {}) => {
    setIsConnecting(true)
    setErrorMessage('')

    try {
      const player = await createPlayer()

      if (activate && typeof player.activateElement === 'function') {
        await player.activateElement()
      }

      if (deviceIdRef.current && isReady) {
        return deviceIdRef.current
      }

      const readyPromise = ensureReadyPromise()
      const connected = await player.connect()
      if (!connected) {
        throw new Error('Spotify refused the AgentMusic web player connection.')
      }

      const readyDeviceId = await readyPromise
      if (activate || wasWebPlaybackEnabled()) {
        rememberWebPlaybackEnabled()
      }
      return readyDeviceId
    } catch (error) {
      resetReadyPromise()
      setErrorMessage(toSdkErrorMessage(error))
      throw error
    } finally {
      setIsConnecting(false)
    }
  }

  const reconnect = async () => {
    resetReadyPromise()
    setIsReady(false)
    return ensureReady({ activate: true })
  }

  useEffect(() => {
    if (!playerRef.current || typeof playerRef.current.setVolume !== 'function') {
      return
    }
    playerRef.current.setVolume(volume).catch(() => {})
  }, [volume])

  useEffect(() => {
    return () => {
      playerRef.current?.disconnect?.()
    }
  }, [])

  useEffect(() => {
    if (!autoReconnect || autoReconnectAttemptedRef.current || !wasWebPlaybackEnabled()) {
      return
    }

    autoReconnectAttemptedRef.current = true
    ensureReady({ activate: false }).catch(() => {})
  }, [autoReconnect])

  return {
    deviceId,
    isReady,
    isConnecting,
    isActive,
    errorMessage,
    playbackState,
    ensureReady,
    reconnect,
  }
}
