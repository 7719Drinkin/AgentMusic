export const PLAYPAUSE = "PLAYPAUSE";
export const CHANGETRACK = "CHANGETRACK";
export const SYNC_PLAYBACK_SESSION = "SYNC_PLAYBACK_SESSION";

export const changePlay = (isPlaying) => {
  return { type: PLAYPAUSE, payload: isPlaying };
};

export const changeTrack = (trackKey) => {
  return { type: CHANGETRACK, payload: trackKey };
};

export const syncPlaybackSession = (sessionData) => {
  return { type: SYNC_PLAYBACK_SESSION, payload: sessionData };
};
