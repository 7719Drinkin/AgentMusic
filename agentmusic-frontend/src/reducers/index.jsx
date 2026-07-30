import { PLAYPAUSE, SYNC_PLAYBACK_SESSION } from "../actions/index";

const INITIAL_STATE = {
  trackData: {
    trackId: null,
    track: null,
    trackName: "",
    trackImg: "",
    trackArtist: "",
    trackArtistId: null,
    albumName: "",
    albumId: null,
    durationMs: 0
  },
  isPlaying: false,
  currentPositionMs: 0,
  playbackMode: 'SEQUENTIAL',
  deviceId: null,
  currentPlaylistId: null,
  currentTrackIndex: null,
};

export const reducer = (state = INITIAL_STATE, action) => {
  switch (action.type) {
    case PLAYPAUSE:
      return {
        ...state,
        isPlaying: action.payload
      };
    case SYNC_PLAYBACK_SESSION:
      return {
        ...state,
        isPlaying: action.payload.isPlaying,
        currentPositionMs: action.payload.currentPositionMs ?? 0,
        playbackMode: action.payload.playbackMode ?? 'SEQUENTIAL',
        deviceId: action.payload.deviceId ?? null,
        currentPlaylistId: action.payload.currentPlaylistId ?? null,
        currentTrackIndex: action.payload.currentTrackIndex ?? null,
        trackData: {
          ...state.trackData,
          trackId: action.payload.trackId ?? null,
          track: action.payload.track ?? null,
          trackName: action.payload.trackName ?? "",
          trackImg: action.payload.trackImg ?? "",
          trackArtist: action.payload.trackArtist ?? "",
          trackArtistId: action.payload.trackArtistId ?? null,
          albumName: action.payload.albumName ?? "",
          albumId: action.payload.albumId ?? null,
          durationMs: action.payload.durationMs ?? 0
        }
      };
    default:
      return state;
  }
};
