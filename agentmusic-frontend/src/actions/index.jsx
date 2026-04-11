export const PLAYPAUSE = "PLAYPAUSE";
export const SYNC_PLAYBACK_SESSION = "SYNC_PLAYBACK_SESSION";

export const changePlay = (isPlaying) => {
  return { type: PLAYPAUSE, payload: isPlaying };
};

export const syncPlaybackSession = (sessionData) => {
  return { type: SYNC_PLAYBACK_SESSION, payload: sessionData };
};
