import { httpRequest } from './http'

export function fetchTrack(trackId) {
  return httpRequest(`/api/music/tracks/${encodeURIComponent(trackId)}`)
}
