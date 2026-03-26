import { httpRequest } from './http'

export function fetchPlaybackSession(userId) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/session`)
}
