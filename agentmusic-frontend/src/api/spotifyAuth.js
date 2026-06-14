import { httpRequest } from './http'

export function fetchWebPlaybackToken() {
  return httpRequest('/api/auth/spotify/web-playback-token')
}
