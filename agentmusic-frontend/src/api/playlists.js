import { httpRequest } from './http'

export function fetchRecentPlaylists(userId, limit = 10) {
  return httpRequest(`/api/playlists/${encodeURIComponent(userId)}?limit=${limit}`)
}

export async function fetchPlaylistDetail(playlistId, userId = 'demo-user') {
  try {
    return await httpRequest(`/api/playlists/${encodeURIComponent(playlistId)}/detail`)
  } catch {
    const recentPlaylists = await fetchRecentPlaylists(userId, 10)
    return recentPlaylists.find((playlist) => playlist.id === playlistId) ?? null
  }
}
