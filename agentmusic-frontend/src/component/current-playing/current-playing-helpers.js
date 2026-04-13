export const QUEUE_NEXT_REQUEST_EVENT = 'agentmusic:queue-next-request'
export const QUEUE_PLAY_REQUEST_EVENT = 'agentmusic:queue-play-request'

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

export function buildCredits(track, artistName) {
  return [
    { label: 'Main Artist', value: artistName || '暂未提供' },
    { label: 'Lyricist', value: 'Spotify Web API 暂未提供' },
    { label: 'Composer', value: 'Spotify Web API 暂未提供' },
    { label: 'Album', value: track?.albumName || '暂未提供' },
  ]
}

export function mapQueueItems(playlistDetail, currentTrackIndex) {
  if (!playlistDetail?.tracks?.length) {
    return []
  }

  return playlistDetail.tracks
    .filter((item) => item?.track)
    .map((item, index) => ({
      trackId: item.track.trackId,
      trackIndex: index,
      songName: item.track.title,
      songArtistId: item.track.artistId,
      songArtist: null,
      songimg: item.track.albumImageUrl || '/image/Playlist/liked-songs.PNG',
      isCurrent: index === Math.max(currentTrackIndex ?? 0, 0),
    }))
}

export function resolveCurrentTrackFromPlaylist(playlistDetail, currentTrackIndex, fallbackTrackData) {
  const playlistTrack = playlistDetail?.tracks?.[currentTrackIndex ?? -1]?.track
  if (playlistTrack) {
    return {
      trackId: playlistTrack.trackId,
      title: playlistTrack.title,
      artistId: playlistTrack.artistId,
      albumName: playlistTrack.albumName,
      albumId: playlistTrack.albumId,
      albumImageUrl: playlistTrack.albumImageUrl,
      durationMs: playlistTrack.durationMs,
    }
  }

  if (!fallbackTrackData?.trackId) {
    return null
  }

  return {
    trackId: fallbackTrackData.trackId,
    title: fallbackTrackData.trackName,
    artistId: fallbackTrackData.trackArtistId,
    albumName: fallbackTrackData.albumName,
    albumId: fallbackTrackData.albumId,
    albumImageUrl: fallbackTrackData.trackImg,
    durationMs: fallbackTrackData.durationMs,
  }
}
