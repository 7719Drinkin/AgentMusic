import { PLAYLIST } from "../data/index";
import { PLAYPAUSE, CHANGETRACK, SYNC_PLAYBACK_SESSION } from "../actions/index";

const DEFAULT_TRACK = PLAYLIST[0].playlistData[0];

const INITIAL_STATE = {
  trackData: {
    trackKey: [0, 0],
    trackId: null,
    track: `${DEFAULT_TRACK.link}`,
    trackName: `${DEFAULT_TRACK.songName}`,
    trackImg: `${DEFAULT_TRACK.songimg}`,
    trackArtist: `${DEFAULT_TRACK.songArtist}`,
    durationMs: 30000
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
    case CHANGETRACK:
      return {
        ...state,
        currentPositionMs: 0,
        trackData: {
          ...state.trackData,
          trackKey: action.payload,
          trackId: null,
          track: `${
            PLAYLIST[action.payload[0]].playlistData[action.payload[1]].link
          }`,
          trackName: `${
            PLAYLIST[action.payload[0]].playlistData[action.payload[1]].songName
          }`,
          trackImg: `${
            PLAYLIST[action.payload[0]].playlistData[action.payload[1]].songimg
          }`,
          trackArtist: `${
            PLAYLIST[action.payload[0]].playlistData[action.payload[1]].songArtist
          }`,
          durationMs: 30000
        }
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
          trackKey: [-1, -1],
          trackId: action.payload.trackId ?? null,
          track: action.payload.track ?? state.trackData.track,
          trackName: action.payload.trackName ?? state.trackData.trackName,
          trackImg: action.payload.trackImg ?? state.trackData.trackImg,
          trackArtist: action.payload.trackArtist ?? state.trackData.trackArtist,
          durationMs: action.payload.durationMs ?? state.trackData.durationMs
        }
      };
    default:
      return state;
  }
};
