import { httpRequest } from './http'

export function fetchTrack(trackId) {
  return httpRequest(`/api/music/tracks/${encodeURIComponent(trackId)}`)
}

export function fetchArtist(artistId) {
  return httpRequest(`/api/music/artists/${encodeURIComponent(artistId)}`)
}
