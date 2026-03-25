package com.agentmusic.agentmusic_backend.client;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public interface SpotifyPlaybackClient {

    void playTrack(String accessToken, String trackId, String deviceId);

    void pause(String accessToken, String deviceId);

    void seek(String accessToken, int positionMs, String deviceId);

    void changePlaybackMode(String accessToken, PlaybackMode playbackMode, String deviceId);
}

