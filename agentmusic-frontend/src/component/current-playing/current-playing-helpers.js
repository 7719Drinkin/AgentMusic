export const QUEUE_NEXT_REQUEST_EVENT = 'agentmusic:queue-next-request'

export function resolveCurrentPlaylistTitle() {
  return '当前播放'
}

export function resolveNextQueueItem() {
  return null
}

export function resolveQueueItems() {
  return []
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
