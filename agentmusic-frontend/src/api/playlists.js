import { httpRequest } from './http'

export function fetchRecentPlaylists(userId, limit = 10) {
  return httpRequest(`/api/playlists/${encodeURIComponent(userId)}?limit=${limit}`)
}
