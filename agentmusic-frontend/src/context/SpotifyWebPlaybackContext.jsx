import { createContext, useContext, useState } from 'react'
import useSpotifyWebPlayback from '../hooks/useSpotifyWebPlayback'

const SpotifyWebPlaybackContext = createContext(null)

export function SpotifyWebPlaybackProvider({ children }) {
  const [volume, setVolume] = useState(1)
  const webPlayback = useSpotifyWebPlayback({ volume, autoReconnect: true })

  return (
    <SpotifyWebPlaybackContext.Provider value={{ ...webPlayback, volume, setVolume }}>
      {children}
    </SpotifyWebPlaybackContext.Provider>
  )
}

export function useSpotifyWebPlaybackContext() {
  const context = useContext(SpotifyWebPlaybackContext)
  if (!context) {
    throw new Error('useSpotifyWebPlaybackContext must be used inside SpotifyWebPlaybackProvider.')
  }
  return context
}
