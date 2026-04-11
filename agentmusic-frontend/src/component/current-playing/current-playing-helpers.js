import { PLAYLIST } from '../../data/index'

export const QUEUE_NEXT_REQUEST_EVENT = 'agentmusic:queue-next-request'

function normalizeIndex(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

export function resolvePlaylistContext(trackData, currentPlaylistId, currentTrackIndex) {
  if (trackData.trackKey?.[0] >= 0) {
    const playlist = PLAYLIST[trackData.trackKey[0]]
    if (playlist) {
      return {
        playlist,
        playlistIndex: trackData.trackKey[0],
        trackIndex: normalizeIndex(trackData.trackKey[1]) ?? 0,
      }
    }
  }

  if (currentPlaylistId) {
    const playlistIndex = PLAYLIST.findIndex(
      (playlist) =>
        playlist.link === currentPlaylistId ||
        playlist.index === currentPlaylistId ||
        playlist.title === currentPlaylistId,
    )

    if (playlistIndex >= 0) {
      return {
        playlist: PLAYLIST[playlistIndex],
        playlistIndex,
        trackIndex: normalizeIndex(currentTrackIndex) ?? 0,
      }
    }
  }

  return null
}

export function resolveCurrentPlaylistTitle(trackData, currentPlaylistId, currentTrackIndex) {
  const context = resolvePlaylistContext(trackData, currentPlaylistId, currentTrackIndex)
  if (context?.playlist?.title) {
    return context.playlist.title
  }

  return currentPlaylistId || '当前播放'
}

export function resolveNextQueueItem(trackData, currentPlaylistId, currentTrackIndex) {
  const context = resolvePlaylistContext(trackData, currentPlaylistId, currentTrackIndex)
  if (!context) {
    return null
  }

  return context.playlist.playlistData[context.trackIndex + 1] || null
}

export function resolveQueueItems(trackData, currentPlaylistId, currentTrackIndex) {
  const context = resolvePlaylistContext(trackData, currentPlaylistId, currentTrackIndex)
  if (!context) {
    return []
  }

  return context.playlist.playlistData.slice(context.trackIndex)
}

export function buildCredits(trackArtist) {
  return [
    { label: 'Main Artist', value: trackArtist || '待接入' },
    { label: 'Lyricist', value: '待接入真实歌词作者' },
    { label: 'Composer', value: '待接入真实作曲信息' },
    { label: 'Producer', value: '待接入真实制作人' },
  ]
}

export function buildArtistSearchLocation(artistName, state = {}) {
  return {
    pathname: '/search',
    search: `?artist=${encodeURIComponent(artistName)}`,
    state: {
      artistName,
      ...state,
    },
  }
}
